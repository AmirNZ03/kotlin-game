package com.example.hokm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.util.Log

class MainActivity : AppCompatActivity() {
    lateinit var engine: GameEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = GameEngine(roundTarget = 7)

        val runBtn = findViewById<Button>(R.id.runSim)
        val out = findViewById<TextView>(R.id.simOut)
        runBtn.setOnClickListener {
            Thread {
                val res = SimulationRunner.run(100)
                val text = "Simulation 100 rounds -> Team A: ${'$'}{res.teamAWins}, Team B: ${'$'}{res.teamBWins}"
                runOnUiThread { out.text = text }
                Log.i("HOKM","$text")
            }.start()
        }
    }
}
