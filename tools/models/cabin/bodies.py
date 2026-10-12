"""Where each family of the game's bodies has its glass, as the interior is fitted to it.

Read off the game's own models by measuring them (the numbers only; nothing of the models is kept),
in tiles from the driver's eye as the mod seats it: x ahead, y to the left, z up. The driver sits on
the left, so the vehicle's middle line is at a negative y.

screen: the windscreen. foot and top are its lower and upper edges, each (x at the corners, x at the
    middle line, z, half its width from the middle line).
side: the side glass. sill_z and sill_half are the foot of the windows and how far out they are;
    top_z and top_half the top edge; a_front the x where the windscreen's pillar meets the sill;
    b the x of the pillar behind the front door, back and front of it; rear_foot and rear_top the x
    where the glass of the rear door or quarter ends at the sill and at the top; None for a body with
    no glass behind the front door.
back: the back window: foot and top as for the screen, or None where the back is a bulkhead.
quarter: the windows in the sides behind the rear doors, where there are any: x of their front and rear edges at
    the sill and at the top, and how far out they are at the sill.
seats: where the script seats each passenger, (x, y) from the driver's eye.
rear_axle: the x of the rear wheels' middle, from the script's wheels scaled as its seats are.
bench: the front seat is a bench, its belts' buckles meeting at the middle.
mirror: the door mirror's head where the game's own body has it (x, how far out from the middle line, z), and how far out
    the door's skin is at the foot of the glass, where its arm meets the door. The race car's body has none: its own.
"""

CAR_NORMAL = {
    "middle": -0.323,
    "screen": {"foot": (0.663, 0.724, -0.224, 0.729), "top": (0.181, 0.242, 0.215, 0.530)},
    "side": {"sill_z": -0.222, "sill_half": 0.729, "top_z": 0.217, "top_half": 0.529, "a_front": 0.663,
             "b": (-0.275, -0.320), "rear_foot": -1.040, "rear_top": -0.882},
    "back": {"foot": (-1.415, -1.504, -0.147, 0.687), "top": (-1.117, -1.225, 0.181, 0.527)},
    "seats": [(0.0, 0.0), (0.0, -0.640), (-0.750, 0.0), (-0.750, -0.640)],
    "rear_axle": -1.270,
    # As the mod's VehicleMeshes.Parts measures the body, from the wheel's middle: the windscreen's foot
    # along and up, the sill up, the glass out from the wheel, the middle line across.
    "measures": (0.3734, 0.0712, 0.0728, 0.4058, -0.3234),
    "mirror": {"head": (0.556, 0.903, -0.203), "skin": 0.770},
    "bench": True,
}

# The wagon has the saloon's front and doors, a window in each side behind the rear doors, and a tailgate.
CAR_STATION_WAGON = dict(CAR_NORMAL)
CAR_STATION_WAGON.update({
    "quarter": {"front_foot": -1.418, "front_top": -1.120, "rear_foot": -2.480, "rear_top": -2.342, "sill_half": 0.687},
    "back": {"foot": (-2.480, -2.502, -0.225, 0.687), "top": (-2.342, -2.376, 0.211, 0.527)},
    "measures": (0.3734, 0.0695, 0.0726, 0.4058, -0.3234),
})

# The hatchbacks: two long doors, a window in each side behind them, a hatch with a sloping back window. The side
# glass of each door begins behind a fixed light under the windscreen's raked pillar.
SMALL_CAR02 = {
    "middle": -0.323,
    "screen": {"foot": (0.852, 0.932, -0.219, 0.713), "top": (0.085, 0.158, 0.209, 0.590)},
    "side": {"sill_z": -0.243, "sill_half": 0.713, "top_z": 0.187, "top_half": 0.596, "a_front": 0.852, "front": (0.343, 0.006),
             "b": (-0.544, -0.556), "rear_foot": -1.164, "rear_top": -0.901},
    "back": {"foot": (-1.447, -1.482, -0.175, 0.638), "top": (-1.003, -1.026, 0.205, 0.572)},
    "seats": [(0.0, 0.0), (0.0, -0.646), (-0.750, 0.0), (-0.750, -0.646)],
    "rear_axle": -1.171,
    "measures": (0.4996, 0.0429, 0.0532, 0.3904, -0.3234),
    "doors": 2,
    "mirror": {"head": (0.425, 0.882, -0.137), "skin": 0.720},
}

SMALL_CAR = {
    "middle": -0.282,
    "screen": {"foot": (0.664, 0.789, -0.185, 0.645), "top": (0.043, 0.121, 0.190, 0.445)},
    "side": {"sill_z": -0.205, "sill_half": 0.660, "top_z": 0.190, "top_half": 0.444, "a_front": 0.664, "front": (0.55, 0.032),
             "b": (-0.505, -0.488), "rear_foot": -1.40, "rear_top": -1.16},
    "back": {"foot": (-1.531, -1.569, -0.178, 0.608), "top": (-1.154, -1.191, 0.157, 0.445)},
    "seats": [(0.0, 0.0), (0.0, -0.564), (-0.680, 0.0), (-0.680, -0.564)],
    "rear_axle": -0.788,
    "measures": (0.4364, 0.1086, 0.0652, 0.3778, -0.2824),
    "doors": 2,
    "mirror": {"head": (0.373, 0.799, -0.147), "skin": 0.663},
}

# The square-bodied trucks. The pickups' cab ends behind the bench at a small back window; the All-Terrain has rear
# doors, a window behind each and a tailgate. Their windscreens stand higher at the top than the side glass.
# The passenger's window foot stands 7mm lower and 4mm further in than the driver's; the side is the passenger's.
PICK_UP_TRUCK = {
    "middle": -0.324,
    "screen": {"foot": (0.595, 0.753, -0.205, 0.672), "top": (0.348, 0.444, 0.225, 0.457)},
    "side": {"sill_z": -0.224, "sill_half": 0.668, "top_z": 0.157, "top_half": 0.522, "a_front": 0.595, "front": (0.52, 0.255),
             "b": (-0.25, -0.25)},
    "back": {"foot": (-0.352, -0.352, -0.19, 0.626), "top": (-0.291, -0.291, 0.225, 0.457)},
    "seats": [(0.0, 0.0), (0.0, -0.640)],
    "rear_axle": -1.190,
    "measures": (0.3101, 0.0780, 0.0790, 0.3480, -0.3240),
    "doors": 2,
    "pillar": "flat",
    "mirror": {"head": (0.304, 0.855, -0.183), "skin": 0.678},
    "bench": True,
}

SUV = {
    "middle": -0.323,
    "screen": {"foot": (0.689, 0.692, -0.200, 0.777), "top": (0.168, 0.168, 0.190, 0.630)},
    "side": {"sill_z": -0.197, "sill_half": 0.776, "top_z": 0.155, "top_half": 0.652, "a_front": 0.689, "front": (0.256, 0.134),
             "b": (-0.305, -0.324), "rear_foot": -1.08, "rear_top": -0.96},
    "quarter": {"front_foot": -1.10, "front_top": -0.979, "rear_foot": -2.03, "rear_top": -1.82, "sill_half": 0.776},
    "back": {"foot": (-2.030, -2.042, -0.195, 0.776), "top": (-1.838, -1.858, 0.160, 0.650)},
    "seats": [(0.0, 0.0), (0.0, -0.640), (-0.780, 0.0), (-0.780, -0.640)],
    "rear_axle": -1.280,
    "measures": (0.3420, 0.0940, 0.0990, 0.4530, -0.3230),
    "doors": 4,
    "pillar": "flat",
    "mirror": {"head": (0.426, 0.963, -0.127), "skin": 0.788},
    "bench": True,
}

# The vans: a cab whose wraparound windscreen rises at its middle, and a box behind it with two doors at the back.
# The passenger van has windows down the box and benches in rows.
VAN = {
    "middle": -0.347,
    "screen": {"foot": (0.601, 0.680, -0.191, 0.799, -0.152), "top": (0.197, 0.328, 0.192, 0.688, 0.288)},
    "side": {"sill_z": -0.205, "sill_half": 0.799, "top_z": 0.192, "top_half": 0.688, "a_front": 0.601, "front": (0.49, 0.168), "b": (-0.27, -0.27)},
    "back": {"foot": (-2.802, -2.858, -0.203, 0.733, -0.200), "top": (-2.684, -2.740, 0.247, 0.603, 0.263)},
    "seats": [(0.0, 0.0), (0.0, -0.694)],
    "rear_axle": -1.735,
    "measures": (0.3126, 0.1004, 0.0890, 0.4525, -0.3469),
    "doors": 2,
    "dome": 0.045,
    "pillar": "flat",
    "mirror": {"head": (0.312, 0.931, -0.179), "skin": 0.803},
}

VAN_SEATS = dict(VAN)
VAN_SEATS.update({
    "seats": [(0.0, 0.0), (0.0, -0.694), (-0.860, 0.0), (-0.860, -0.694), (-1.820, 0.0), (-1.820, -0.694)],
    "windows": [(-0.354, -0.354, -1.39, -1.39), (-1.39, -1.39, -2.73, -2.616)],
    "window": {"sill_z": -0.203, "top_z": 0.232, "sill_half": 0.799, "top_half": 0.660},
})

# The Mercia Lang: a two-seat coupe with a long fastback over a luggage shelf behind the seats.
CAR_LUXURY = {
    "middle": -0.323,
    "screen": {"foot": (0.848, 0.884, -0.162, 0.743), "top": (0.225, 0.244, 0.200, 0.524)},
    "side": {"sill_z": -0.180, "sill_half": 0.824, "top_z": 0.172, "top_half": 0.608, "a_front": 0.848, "front": (0.356, 0.130),
             "b": (-0.495, -0.530)},
    "back": {"foot": (-1.350, -1.386, -0.112, 0.696), "top": (-0.762, -0.776, 0.198, 0.525)},
    "seats": [(0.0, 0.0), (0.0, -0.660)],
    "rear_axle": -0.970,
    "measures": (0.5280, 0.0960, 0.1120, 0.5010, -0.3230),
    "doors": 2,
    "mirror": {"head": (0.434, 0.969, -0.103), "skin": 0.834},
}

# The early-nineties saloons: the Dash Elite and the Chevalier Primani.
MODERN_CAR = {
    "middle": -0.323,
    "screen": {"foot": (0.822, 0.930, -0.240, 0.704, -0.212), "top": (0.154, 0.233, 0.178, 0.509, 0.190)},
    "side": {"sill_z": -0.240, "sill_half": 0.731, "top_z": 0.198, "top_half": 0.530, "a_front": 0.822, "front": (0.620, 0.064),
             "b": (-0.320, -0.360), "rear_foot": -1.120, "rear_top": -0.970},
    "back": {"foot": (-1.537, -1.660, -0.070, 0.602, -0.016), "top": (-1.133, -1.186, 0.193, 0.526, 0.220)},
    "seats": [(0.0, 0.0), (0.0, -0.660), (-0.850, 0.0), (-0.850, -0.660)],
    "rear_axle": -1.320,
    "measures": (0.4700, 0.0300, 0.0210, 0.4080, -0.3230),
    "doors": 4,
    "mirror": {"head": (0.515, 0.917, -0.165), "skin": 0.787},
    # Its windscreen pillar meets the body so close at its foot that a cap round it would stand outside.
    "pillar_cap": False
}

MODERN_CAR02 = {
    "middle": -0.323,
    "screen": {"foot": (0.820, 0.900, -0.240, 0.713, -0.223), "top": (0.182, 0.255, 0.208, 0.495, 0.210)},
    "side": {"sill_z": -0.245, "sill_half": 0.713, "top_z": 0.186, "top_half": 0.506, "a_front": 0.820, "front": (0.412, 0.105),
             "b": (-0.315, -0.340), "rear_foot": -1.205, "rear_top": -0.948},
    "back": {"foot": (-1.540, -1.574, -0.170, 0.638, -0.165), "top": (-1.125, -1.148, 0.205, 0.481, 0.205)},
    "seats": [(0.0, 0.0), (0.0, -0.640), (-0.830, 0.0), (-0.830, -0.640)],
    "rear_axle": -1.320,
    "measures": (0.5484, 0.0429, 0.0526, 0.3904, -0.3234),
    "doors": 4,
    "mirror": {"head": (0.482, 0.884, -0.203), "skin": 0.720},
}

# The Chevalier Cossette: a low two-seat sports car, its roof a hand over the driver's eye, a big glass hatch behind.
SPORTS_CAR = {
    "middle": -0.274,
    "screen": {"foot": (0.743, 0.836, -0.250, 0.602, -0.233), "top": (0.190, 0.254, 0.079, 0.416, 0.110)},
    "side": {"sill_z": -0.262, "sill_half": 0.628, "top_z": 0.082, "top_half": 0.432, "a_front": 0.743, "front": (0.596, 0.116),
             "b": (-0.416, -0.555), "rear_foot": -0.923, "rear_top": -0.585},
    # Its trim behind the quarter glass runs a long way back to the fastback's edge, round the body's corner.
    "c_pillar": "fastback",
    "back": {"foot": (-1.306, -1.358, -0.205, 0.576, -0.190), "top": (-0.586, -0.615, 0.070, 0.432, 0.100)},
    "seats": [(0.0, 0.0), (0.0, -0.548)],
    "rear_axle": -0.730,
    "measures": (0.4840, 0.0470, 0.0300, 0.3540, -0.2740),
    "doors": 2,
    "dome": 0.012,
    "mirror": {"head": (0.493, 0.752, -0.229), "skin": 0.643},
}

# The race cars: a coupe stripped for the track, its roof low over a caged cockpit.
RACE_CAR = {
    "middle": -0.249,
    "screen": {"foot": (0.849, 0.892, -0.164, 0.564), "top": (0.255, 0.392, 0.084, 0.512, 0.103)},
    "side": {"sill_z": -0.147, "sill_half": 0.591, "top_z": 0.090, "top_half": 0.514, "a_front": 0.849, "front": (0.545, 0.065),
             "b": (-0.201, -0.201)},
    "back": {"foot": (-1.041, -1.065, -0.149, 0.556), "top": (-0.566, -0.549, 0.083, 0.482, 0.106)},
    "seats": [(0.0, 0.0), (0.0, -0.498)],
    "rear_axle": -0.734,
    "measures": (0.5401, 0.1303, 0.1490, 0.3419, -0.2488),
    "doors": 2,
    "dome": 0.010,
    "race": True,
    "mirror": {"head": (0.470, 0.730, -0.200), "skin": 0.603},
}

# The Dash Rancher: a short square off-roader with a hard top, an upright flat windscreen and a load space behind.
OFF_ROAD = {
    "middle": -0.323,
    "screen": {"foot": (0.609, 0.617, -0.300, 0.688, -0.283), "top": (0.428, 0.436, 0.160, 0.625)},
    "side": {"sill_z": -0.270, "sill_half": 0.688, "top_z": 0.140, "top_half": 0.625, "a_front": 0.609, "front": (0.499, 0.348),
             "b": (-0.188, -0.384), "rear_foot": -0.985, "rear_top": -0.914},
    "back": {"foot": (-1.154, -1.170, -0.235, 0.572), "top": (-1.045, -1.060, 0.160, 0.572)},
    "seats": [(0.0, 0.0), (0.0, -0.640)],
    "rear_axle": -0.620,
    "measures": (0.2690, -0.0030, -0.0030, 0.3650, -0.3230),
    "doors": 2,
    "pillar": "post",
    "c_pillar": "corner",
    "mirror": {"head": (0.443, 0.832, -0.229), "skin": 0.688},
}

# The Chevalier Step Van: an upright walk-in van, its flat windscreen standing straight up, a tall box behind.
STEP_VAN = {
    "middle": -0.364,
    "screen": {"foot": (0.879, 0.899, -0.240, 0.812), "top": (0.700, 0.722, 0.344, 0.812)},
    "side": {"sill_z": -0.240, "sill_half": 0.812, "top_z": 0.340, "top_half": 0.812, "a_front": 0.879, "front": (0.433, 0.429),
             "b": (-0.235, -0.235)},
    "back": {"foot": (-2.630, -2.640, -0.240, 0.380), "top": (-2.630, -2.640, 0.410, 0.380)},
    "seats": [(0.0, 0.0), (0.0, -0.720)],
    "rear_axle": -1.630,
    "measures": (0.5452, 0.0523, 0.0556, 0.4478, -0.3637),
    "doors": 2,
    "dome": 0.03,
    "pillar": "flat",
    "mirror": {"head": (0.464, 0.979, -0.213), "skin": 0.811},
}

FAMILIES = {
    "Base.CarLuxury": CAR_LUXURY,
    "Base.ModernCar": MODERN_CAR,
    "Base.ModernCar02": MODERN_CAR02,
    "Base.SportsCar": SPORTS_CAR,
    "Base.RaceCar": RACE_CAR,
    "Base.OffRoad": OFF_ROAD,
    "Base.StepVan": STEP_VAN,
    "Base.Van": VAN,
    "Base.VanSeats": VAN_SEATS,
    "Base.PickUpTruck": PICK_UP_TRUCK,
    "Base.PickUpVan": PICK_UP_TRUCK,
    "Base.SUV": SUV,
    "Base.SmallCar02": SMALL_CAR02,
    "Base.SmallCar": SMALL_CAR,
    "Base.CarNormal": CAR_NORMAL,
    "Base.CarStationWagon": CAR_STATION_WAGON,
}

# The glasshouse the mod's own drawn cabin (Cabins.HOUSES) builds over each family's driver: how steeply its windscreen
# stands, in degrees, and how far above the eye its rail is, in the cabin's units (Cabins.SCALE of a tile). The mirror on
# the screen hangs at the height that cabin hangs it.
GLASSHOUSES = {
    "Base.CarNormal": (42.0, 0.20), "Base.CarLuxury": (40.0, 0.20), "Base.CarStationWagon": (44.0, 0.21), "Base.ModernCar02": (34.0, 0.19),
    "Base.ModernCar": (33.0, 0.19), "Base.OffRoad": (62.0, 0.25), "Base.PickUpTruck": (52.0, 0.24), "Base.PickUpVan": (52.0, 0.24),
    "Base.RaceCar": (34.0, 0.16), "Base.SmallCar02": (44.0, 0.20), "Base.SmallCar": (46.0, 0.20), "Base.SportsCar": (30.0, 0.14),
    "Base.StepVan": (80.0, 0.48), "Base.SUV": (50.0, 0.27), "Base.Van": (58.0, 0.33), "Base.VanSeats": (58.0, 0.33),
}
for _family, _spec in FAMILIES.items():
    _spec["glasshouse"] = GLASSHOUSES[_family]
