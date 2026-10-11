package de.lardtom.propwash.content;

public enum CraftingMaterial {
    CARBON_PLATE("carbon_plate", false),
    COPPER_COIL("copper_coil", false),
    CIRCUIT_BOARD("circuit_board", false),
    LIPO_CELL("lipo_cell", false),
    HD_CHIP("hd_chip", true);

    private final String key;
    private final boolean rare;

    CraftingMaterial(String key, boolean rare) {
        this.key = key;
        this.rare = rare;
    }

    public String key() {
        return this.key;
    }

    public boolean rare() {
        return this.rare;
    }
}
