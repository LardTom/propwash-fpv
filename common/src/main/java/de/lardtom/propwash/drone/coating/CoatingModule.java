package de.lardtom.propwash.drone.coating;

public final class CoatingModule {
    private CoatingModule() {
    }

    public static void init() {
        CoatingComponents.bootstrap();
        WaterproofCoatingRecipe.bootstrap();
    }
}
