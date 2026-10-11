SEGMENTS = 24
LIP_OVERLAP = 0.05
TYPICAL_SEAT = [(31.0, 7.0), (45.0, 8.75), (65.0, 12.5), (90.0, 13.5), (127.0, 18.0), (180.0, 19.5)]


def clearance(prop_mm):
    return 1.1 if prop_mm <= 50 else 1.5


def radius(prop_mm):
    return round(prop_mm / 2 + clearance(prop_mm), 2)


def wall(prop_mm):
    return round(min(3.0, max(0.8, 0.35 + 0.016 * prop_mm)), 2)


def lip(prop_mm):
    return round(max(0.45, 0.55 * wall(prop_mm)), 2), round(max(0.8, 0.02 * prop_mm), 2)


def blade_top(prop_mm):
    return 1.75 + 0.024 * prop_mm


def margin(prop_mm):
    return 1.0 if prop_mm <= 50 else 1.2


def top(hub_mm, prop_mm):
    return round(hub_mm + blade_top(prop_mm) + margin(prop_mm), 2)


def ring(m, cx, cz, y0, y1, prop_mm, material, lip_material=None, lip_name="duct_lip", with_lip=True):
    r = radius(prop_mm)
    w = wall(prop_mm)
    m.ring(cx, cz, y0, y1, r, w, SEGMENTS, material, name="duct")
    if with_lip:
        lw, lh = lip(prop_mm)
        m.ring(cx, cz, y1 - lh, y1, r + w - LIP_OVERLAP, lw, SEGMENTS, lip_material or material, name=lip_name)
    return r, w


def typical_seat(prop_mm):
    points = TYPICAL_SEAT
    if prop_mm <= points[0][0]:
        return points[0][1]
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        if prop_mm <= x1:
            return y0 + (y1 - y0) * (prop_mm - x0) / (x1 - x0)
    return points[-1][1]


def motor_height(definition):
    arms = definition.get("arms")
    if isinstance(arms, dict) and arms:
        ys = [float(v["pos"][1]) for v in arms.values() if isinstance(v, dict) and "pos" in v]
        if ys:
            return sum(ys) / len(ys)
    wb = float(definition["wheelbase_mm"])
    return 3.0 if wb >= 200 else 2.5 if wb >= 100 else 1.5
