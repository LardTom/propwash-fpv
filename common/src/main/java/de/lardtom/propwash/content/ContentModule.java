package de.lardtom.propwash.content;

import de.lardtom.propwash.content.paint.PaintService;

public final class ContentModule {
    private ContentModule() {
    }

    public static void init() {
        ContentItems.bootstrap();
        ContentMaterials.bootstrap();
        ContentBlocks.bootstrap();
        ContentTab.bootstrap();
        PropRepairRecipe.bootstrap();
        PaintService.init();
    }
}
