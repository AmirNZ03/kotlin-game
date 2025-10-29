package com.example.hokm

import kotlin.random.Random

class GameEngine(val roundTarget: Int = 7) {
    val players = listOf(
        Player(1, "You", Team.A, isHuman = true),
        Player(2, "P 2", Team.B),
        Player(3, "P 3", Team.A),
        Player(4, "P 4", Team.B)
    )

    var deck = DeckUtils.fullDeck()
    var hokmSuit: Suit? = null
    var dealerId: Int = (1..4).random()
    var teamAScore = 0
    var teamBScore = 0

    val playedCards = mutableListOf<Card>()
    val currentTrick = mutableListOf<Pair<Int, Card>>()

    fun resetForRound() {
        playedCards.clear()
        currentTrick.clear()
        deck = DeckUtils.fullDeck()
        players.forEach { it.reset() }
        dealerId = (1..4).random()
        hokmSuit = null
        teamAScore = 0
        teamBScore = 0
    }

    fun dealInitial() {
        deck = DeckUtils.fullDeck()
        players.forEach { it.reset() }
        repeat(5) {
            players.forEach { p -> p.hand.add(deck.removeAt(0)) }
        }
        val dealer = players.first { it.id == dealerId }
        if (!dealer.isHuman) {
            hokmSuit = DeckUtils.pickRandomSuitFromFive(dealer.hand)
        }
    }

    fun dealRemaining() {
        players.forEach { p -> repeat(8) { p.hand.add(deck.removeAt(0)) } }
        val human = players.first { it.isHuman }
        human.hand.sortWith(compareByDescending<Card> { it.suit == hokmSuit }.thenByDescending { it.value() })
    }

    fun playCard(playerId: Int, card: Card) {
        currentTrick.add(playerId to card)
        val pl = players.first { it.id == playerId }
        pl.hand.remove(card)
    }

    fun resolveTrickAndReturnWinnerId(): Int {
        val winnerId = evaluateTrickWinnerIdPreview()
        currentTrick.forEach { playedCards.add(it.second) }
        currentTrick.clear()
        return winnerId
    }

    fun evaluateTrickWinnerIdPreview(): Int {
        if (currentTrick.isEmpty()) throw IllegalStateException("No cards in trick")
        val leadSuit = currentTrick.first().second.suit
        val hokm = hokmSuit
        val trumps = currentTrick.filter { it.second.suit == hokm }
        if (trumps.isNotEmpty()) {
            val best = trumps.maxByOrNull { it.second.value() }!!
            return best.first
        }
        val same = currentTrick.filter { it.second.suit == leadSuit }
        val best = same.maxByOrNull { it.second.value() }!!
        return best.first
    }

    // full round simulation with SmartTeamAI for non-human players; returns winning team
    fun playFullRoundSimulation(): Team {
        resetForRound()
        // deal
        dealInitial()
        // if dealer human, assume human picks first suit (for sim choose random)
        if (players.first { it.id == dealerId }.isHuman) {
            hokmSuit = listOf(Suit.HEART, Suit.SPADE, Suit.DIAMOND, Suit.CLUB).random()
        } else {
            hokmSuit = DeckUtils.pickRandomSuitFromFive(players.first { it.id == dealerId }.hand)
        }
        dealRemaining()
        // start with dealer as leader
        var leaderIndex = players.indexOfFirst { it.id == dealerId }
        val tricksWon = mutableMapOf(Team.A to 0, Team.B to 0)
        while (tricksWon.values.maxOrNull()!! < 7) {
            // play a trick of 4 cards
            currentTrick.clear()
            for (i in 0 until 4) {
                val p = players[(leaderIndex + i) % 4]
                val card = if (p.isHuman) {
                    // simulate human as random legal play for the purposes of this automated sim
                    SmartTeamAI.simulateChooseRandomLegal(p, this)
                } else {
                    SmartTeamAI.chooseCardTeamAware(p, this)
                }
                playCard(p.id, card)
            }
            val winnerId = resolveTrickAndReturnWinnerId()
            val winner = players.first { it.id == winnerId }
            tricksWon[winner.team] = tricksWon[winner.team]!! + 1
            leaderIndex = players.indexOfFirst { it.id == winnerId }
            // early termination for 7-0 rule scoring handled by caller
        }
        return if (tricksWon[Team.A]!! > tricksWon[Team.B]!!) Team.A else Team.B
    }

}
