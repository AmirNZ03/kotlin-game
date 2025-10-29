package com.example.hokm

enum class Suit(val symbol: String) { HEART("❤️"), SPADE("♠️"), DIAMOND("♦️"), CLUB("♣️") }

data class Card(val rank: String, val suit: Suit) {
    fun value(): Double {
        return when (rank) {
            "A" -> 16.0
            "K" -> 14.0
            "Q" -> 11.5
            "J" -> 10.0
            else -> rank.toInt().toDouble() / 2.0 + 4.0
        }
    }
    override fun toString(): String = "$rank${'$'}{suit.symbol}"
}

enum class Team { A, B }

data class Player(val id: Int, val name: String, val team: Team, val isHuman: Boolean = false) {
    val hand = mutableListOf<Card>()
    fun reset() { hand.clear() }
}
