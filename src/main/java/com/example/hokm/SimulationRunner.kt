package com.example.hokm

object SimulationRunner {
    fun run(n: Int = 100) : SimulationResult {
        val result = SimulationResult()
        val engine = GameEngine(roundTarget = 7)
        for (i in 0 until n) {
            val winner = engine.playFullRoundSimulation()
            if (winner == Team.A) result.teamAWins++ else result.teamBWins++
        }
        return result
    }
}

data class SimulationResult(var teamAWins: Int = 0, var teamBWins: Int = 0)
