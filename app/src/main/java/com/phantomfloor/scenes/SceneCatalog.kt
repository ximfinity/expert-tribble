package com.phantomfloor.scenes

import com.phantomfloor.games.BalloonPopGame
import com.phantomfloor.games.ColoringGame
import com.phantomfloor.games.DefuseBombGame
import com.phantomfloor.games.FloorIsLavaGame
import com.phantomfloor.games.SimonGame
import com.phantomfloor.games.StompSpiderGame
import com.phantomfloor.games.TicTacToeGame
import com.phantomfloor.games.WhackAMoleGame
import com.phantomfloor.scary.EyesInTheDarkScene
import com.phantomfloor.scary.GraspingHandsScene
import com.phantomfloor.scary.JumpScareScene

/** Every scene in the app, in control-panel order. Scene ids are persisted; never rename them. */
object SceneCatalog {
    fun create(): List<Scene> = listOf(
        FogScene(),
        SmokeScene(),
        SwarmScene(),
        CursedFluidScene(),
        LivingWaterScene(),
        JumpScareScene(),
        EyesInTheDarkScene(),
        GraspingHandsScene(),
        SnowScene(SnowScene.Theme.SNOW),
        SnowScene(SnowScene.Theme.CHRISTMAS),
        SnowScene(SnowScene.Theme.HANUKKAH),
        LeavesScene(),
        GardenScene(),
        FireworksScene(),
        FirefliesScene(),
        PoolScene(),
        StompSpiderGame(),
        WhackAMoleGame(),
        DefuseBombGame(),
        TicTacToeGame(),
        SimonGame(),
        BalloonPopGame(),
        FloorIsLavaGame(),
        ColoringGame(),
    )
}
