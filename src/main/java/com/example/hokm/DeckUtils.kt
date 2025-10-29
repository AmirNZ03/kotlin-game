package com.example.hokm

object DeckUtils {
    fun fullDeck(): MutableList<Card> {
        val ranks = listOf("2","3","4","5","6","7","8","9","10","J","Q","K","A")
        val suits = listOf(Suit.HEART, Suit.SPADE, Suit.DIAMOND, Suit.CLUB)
        val deck = mutableListOf<Card>()
        for (s in suits) for (r in ranks) deck.add(Card(r,s))
        deck.shuffle()
        return deck
    }

    fun pickRandomSuitFromFive(cards: List<Card>): Suit {
        val map = Suit.values().associateWith { 0.0 }.toMutableMap()
        for (c in cards) map[c.suit] = map[c.suit]!! + c.value()
        return map.maxByOrNull { it.value }!!.key
    }
}
