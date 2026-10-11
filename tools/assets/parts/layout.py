import math

FRAME_KEYS = ["whoop_65", "cinewhoop_3", "freestyle_5", "race_5", "long_range_7"]
STACK_KEYS = ["aio_whoop", "aio_20", "stack_30", "creative_fc"]
MOTOR_KEYS = ["m0802_22000", "m1404_4600", "m2004_3000", "m2207_1750", "m2306_1860", "m2807_1300"]
PROP_KEYS = ["p31_4b", "p31_3b", "p3_3b", "p51_3b", "p7_2b"]
BATTERY_KEYS = ["b1s_300_hv", "b4s_650", "b6s_1100", "b6s_1300", "b6s_4000_lion"]
VIDEO_KEYS = ["analog_nano", "analog_race", "digital_lite", "digital_hd"]
ACCESSORY_KEYS = ["led_strip", "action_cam_light", "action_cam_heavy", "action_cam_flashback", "gripper"]
ACCESSORY_BASES = {"top": "mount_top"}
MOUNT_KEYS = ["top", "bottom"]

WHEELBASE_MM = {"whoop_65": 65, "cinewhoop_3": 142, "freestyle_5": 225, "race_5": 215, "long_range_7": 295}

MOTOR_SEAT_MM = {
    "m0802_22000": 7.0,
    "m1404_4600": 12.5,
    "m2004_3000": 13.5,
    "m2207_1750": 18.0,
    "m2306_1860": 18.0,
    "m2807_1300": 19.5,
}

MOTOR_SPIN = ["cw", "ccw", "ccw", "cw"]


def half_axis(frame_key):
    return round(WHEELBASE_MM[frame_key] / 2.0 / math.sqrt(2.0), 2)


FRAMES = {
    "whoop_65": {
        "motor_y": -1.5,
        "prop_height": MOTOR_SEAT_MM["m0802_22000"],
        "stack": [0.0, 0.0, 0.0],
        "battery": [0.0, -3.7, 1.0],
        "battery_under": True,
        "video": [0.0, 13.9, -10.2],
        "camera_tilt_default": 12.0,
        "accessories": {"bottom": {"position": [0.0, 0.5, -25.6], "tilt": 90.0, "length": 10.0}},
        "preset": ("aio_whoop", "m0802_22000", "p31_4b", "analog_nano", "b1s_300_hv"),
    },
    "cinewhoop_3": {
        "motor_y": -0.3,
        "prop_height": MOTOR_SEAT_MM["m1404_4600"],
        "stack": [0.0, 0.0, 0.0],
        "battery": [0.0, 24.0, 6.0],
        "video": [0.0, 28.3, -38.2],
        "camera_tilt_default": 10.0,
        "accessories": {"top": {"position": [0.0, 44.7, -47.0], "tilt": 10.0},
                        "bottom": {"position": [0.0, -3.0, -24.0], "tilt": 30.0, "length": 26.0}},
        "preset": ("aio_20", "m1404_4600", "p3_3b", "digital_lite", "b4s_650"),
    },
    "freestyle_5": {
        "motor_y": -0.3,
        "prop_height": MOTOR_SEAT_MM["m2207_1750"],
        "stack": [0.0, 0.0, 0.0],
        "battery": [0.0, 28.5, 4.0],
        "video": [0.0, 12.5, -50.0],
        "camera_tilt_default": 28.0,
        "accessories": {"top": {"position": [0.0, 34.7, -69.0], "tilt": 22.0},
                        "bottom": {"position": [0.0, -3.0, -56.0], "tilt": 30.0, "length": 34.0}},
        "preset": ("stack_30", "m2207_1750", "p51_3b", "digital_hd", "b6s_1300"),
    },
    "race_5": {
        "motor_y": -0.3,
        "stretch": 1.2,
        "prop_height": MOTOR_SEAT_MM["m2306_1860"],
        "stack": [0.0, 0.0, 0.0],
        "battery": [0.0, -2.8, 4.0],
        "battery_under": True,
        "video": [0.0, 12.5, -44.0],
        "camera_tilt_default": 38.0,
        "accessories": {"top": {"position": [0.0, 26.7, -67.0], "tilt": 20.0},
                        "bottom": {"position": [0.0, -2.0, -48.0], "tilt": 30.0, "length": 28.0}},
        "preset": ("stack_30", "m2306_1860", "p51_3b", "analog_race", "b6s_1100"),
    },
    "long_range_7": {
        "motor_y": -0.3,
        "prop_height": MOTOR_SEAT_MM["m2807_1300"],
        "stack": [0.0, 0.0, 0.0],
        "battery": [0.0, 32.5, 4.0],
        "video": [0.0, 14.0, -56.0],
        "camera_tilt_default": 20.0,
        "accessories": {"top": {"position": [0.0, 38.7, -68.0], "tilt": 20.0},
                        "bottom": {"position": [0.0, -2.5, -60.0], "tilt": 30.0, "length": 36.0}},
        "preset": ("stack_30", "m2807_1300", "p7_2b", "digital_hd", "b6s_4000_lion"),
    },
}


def battery_under(frame_key):
    return FRAMES[frame_key].get("battery_under", False)


def battery_placement(frame_key, battery_key, has_model, top_of):
    anchor = FRAMES[frame_key]["battery"]
    base = f"battery/{battery_key}"
    if not battery_under(frame_key):
        return base, anchor
    under = base + "_under"
    if has_model(under):
        return under, anchor
    return base, [anchor[0], round(anchor[1] - top_of(base), 4), anchor[2]]


def motor_axes(frame_key):
    h = half_axis(frame_key)
    stretch = FRAMES[frame_key].get("stretch", 1.0)
    hx = round(h * math.sqrt(2.0 / (1.0 + stretch * stretch)), 2)
    hz = round(math.sqrt(2.0 * h * h - hx * hx), 2)
    return hx, hz


def motor_positions(frame_key):
    hx, hz = motor_axes(frame_key)
    y = FRAMES[frame_key]["motor_y"]
    return [[-hx, y, -hz], [hx, y, -hz], [-hx, y, hz], [hx, y, hz]]


def layout_json():
    frames = {}
    for key in FRAME_KEYS:
        f = FRAMES[key]
        frames[key] = {
            "motors": motor_positions(key),
            "motor_spin": list(MOTOR_SPIN),
            "prop_height": f["prop_height"],
            "stack": f["stack"],
            "battery": f["battery"],
            "video": f["video"],
            "camera_tilt_default": f["camera_tilt_default"],
            "accessories": {mount: dict(anchor) for mount, anchor in f["accessories"].items()},
        }
    return {"frames": frames, "motor_seat": dict(MOTOR_SEAT_MM)}
