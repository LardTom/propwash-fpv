package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class SamplePath {
    public static final String RESOURCE = "assets/propwash/flashback/sample_flight.bin";
    public static final int MAGIC = 0x50574650;
    public static final int VERSION = 1;
    public static final int MAX_TICKS = 20 * 60 * 5;

    private SamplePath() {
    }

    public static List<TickPose> read(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        if (data.readInt() != MAGIC || data.readInt() != VERSION) {
            throw new IOException("not a Propwash sample flight");
        }
        int count = data.readInt();
        if (count < 2 || count > MAX_TICKS) {
            throw new IOException("bad length " + count);
        }
        double ox = data.readDouble();
        double oy = data.readDouble();
        double oz = data.readDouble();
        List<TickPose> poses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double x = ox + data.readFloat();
            double y = oy + data.readFloat();
            double z = oz + data.readFloat();
            Quaterniond q = new Quaterniond(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat()).normalize();
            double fov = data.readFloat();
            poses.add(new TickPose(i, x, y, z, q, fov));
        }
        return poses;
    }

    public static void write(OutputStream out, List<TickPose> poses) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeInt(poses.size());
        TickPose origin = poses.getFirst();
        data.writeDouble(origin.x());
        data.writeDouble(origin.y());
        data.writeDouble(origin.z());
        for (TickPose pose : poses) {
            data.writeFloat((float) (pose.x() - origin.x()));
            data.writeFloat((float) (pose.y() - origin.y()));
            data.writeFloat((float) (pose.z() - origin.z()));
            Quaterniond q = pose.rotation();
            data.writeFloat((float) q.x);
            data.writeFloat((float) q.y);
            data.writeFloat((float) q.z);
            data.writeFloat((float) q.w);
            data.writeFloat((float) pose.fov());
        }
        data.flush();
    }

    public static List<TickPose> load() throws IOException {
        InputStream in = SamplePath.class.getResourceAsStream("/" + RESOURCE);
        if (in == null) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            in = loader == null ? null : loader.getResourceAsStream(RESOURCE);
        }
        if (in == null) {
            throw new IOException("missing " + RESOURCE);
        }
        try (InputStream stream = in) {
            return read(stream);
        }
    }
}
