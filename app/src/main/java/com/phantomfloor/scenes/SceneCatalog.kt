package com.phantomfloor.scenes

/** Every scene in the app, in control-panel order. Scene ids are persisted; never rename them. */
object SceneCatalog {
    fun create(): List<Scene> = listOf(
        FogScene(),
        SwarmScene(),
        CursedFluidScene(),
        LivingWaterScene(),
    )
}
