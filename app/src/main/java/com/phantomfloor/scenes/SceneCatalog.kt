package com.phantomfloor.scenes

/** Every scene in the app, in control-panel order. Scene ids are persisted; never rename them. */
object SceneCatalog {
    fun create(): List<Scene> = listOf(
        FogScene(),
        SmokeScene(),
        SwarmScene(),
        CursedFluidScene(),
        LivingWaterScene(),
        SnowScene(SnowScene.Theme.SNOW),
        SnowScene(SnowScene.Theme.CHRISTMAS),
        SnowScene(SnowScene.Theme.HANUKKAH),
        LeavesScene(),
        GardenScene(),
        FireworksScene(),
        FirefliesScene(),
        PoolScene(),
    )
}
