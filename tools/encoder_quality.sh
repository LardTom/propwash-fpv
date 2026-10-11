#!/bin/bash
set -eu
if [ $# -lt 1 ]; then
  echo "Aufruf: tools/encoder_quality.sh <video.mp4|clip.yuv> [frames=120] [quality=23] [aq=1.0] [threads=6] [bitrate=32300000] [preset=0] [grain=0 grainlo=0 grainhi=0.2 grainchroma=0.1 graincell=1] [deblock=1]" >&2
  echo "Nur zur Analyse: braucht ffmpeg, misst PSNR/SSIM des eigenen H.264-Encoders gegen die Quelle (1920x1080, 60 fps)." >&2
  exit 2
fi
JDK=/opt/homebrew/opt/openjdk@25/bin
ROOT=$(cd "$(dirname "$0")/.." && pwd)
INPUT=$1
shift
WORK=$(mktemp -d /tmp/pw-encoder-quality.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes"
"$JDK/javac" -nowarn -encoding UTF-8 -d "$WORK/classes" \
  "$ROOT"/common/src/main/java/de/lardtom/propwash/video/record/h264/*.java \
  "$ROOT"/common/src/test/java/de/lardtom/propwash/video/record/h264/EncoderBench.java
FRAMES=120
for arg in "$@"; do
  case "$arg" in frames=*) FRAMES=${arg#frames=} ;; esac
done
SOURCE="$WORK/source.yuv"
case "$INPUT" in
  *.yuv) SOURCE=$INPUT ;;
  *) ffmpeg -v error -i "$INPUT" -frames:v "$FRAMES" -vf "scale=1920:1080" -f rawvideo -pix_fmt yuv420p -y "$SOURCE" ;;
esac
REFERENCE=$SOURCE
EXTRA=()
for arg in "$@"; do
  case "$arg" in grain=*) REFERENCE="$WORK/grained.yuv"; EXTRA=("src=$REFERENCE") ;; esac
done
"$JDK/java" -Xmx3g -cp "$WORK/classes" de.lardtom.propwash.video.record.h264.EncoderBench in="$SOURCE" out="$WORK/out.h264" \
  ${EXTRA[@]+"${EXTRA[@]}"} "$@"
ffmpeg -v info -framerate 60 -i "$WORK/out.h264" -f rawvideo -framerate 60 -pix_fmt yuv420p -s 1920x1080 -i "$REFERENCE" \
  -lavfi "[0:v]setpts=N/(60*TB),split[a][b];[1:v]setpts=N/(60*TB),split[c][d];[a][c]psnr=shortest=1;[b][d]ssim=shortest=1" -f null - 2>&1 \
  | grep -o "PSNR y:[0-9.]* u:[0-9.]* v:[0-9.]* average:[0-9.]*\|SSIM Y:[0-9.]*.*All:[0-9.]*"
