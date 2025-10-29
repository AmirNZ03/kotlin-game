package com.example.hokm

import kotlin.random.Random

object SmartTeamAI {

    // public entry used by engine simulation
    fun chooseCardTeamAware(player: Player, engine: GameEngine): Card {
        return chooseCard(player, engine, monteCarloSims = 50) // default sims
    }

    // chooses a card with optional Monte-Carlo lookahead
    fun chooseCard(player: Player, engine: GameEngine, monteCarloSims: Int = 0): Card {
        val leadSuit = if (engine.currentTrick.isEmpty()) null else engine.currentTrick.first().second.suit
        val playable = if (leadSuit == null) player.hand.toList() else {
            val same = player.hand.filter { it.suit == leadSuit }
            if (same.isNotEmpty()) same else player.hand.toList()
        }

        // If montecarlo sims requested, evaluate each playable candidate
        if (monteCarloSims > 0 && playable.size > 1) {
            val scores = playable.associateWith { candidate ->
                estimateCardScore(player, candidate, engine, monteCarloSims)
            }
            val best = scores.maxByOrNull { it.value }!!.key
            player.hand.remove(best)
            return best
        }

        // fallback deterministic heuristics similar to prior design
        if (leadSuit != null && playable.any { it.suit == leadSuit }) {
            val sameSuit = playable.filter { it.suit == leadSuit }
            val winningCandidates = sameSuit.filter { card -> allHigherCardsPlayed(card, engine) }
            if (winningCandidates.isNotEmpty()) {
                if (!isTeammateLikelyWinner(player, engine)) {
                    val chosen = winningCandidates.minByOrNull { it.value() }!!
                    player.hand.remove(chosen)
                    return chosen
                }
            }
            if (isTeammateLikelyWinner(player, engine)) {
                val chosen = sameSuit.minByOrNull { it.value() }!!
                player.hand.remove(chosen)
                return chosen
            }
            val chosen = sameSuit.minByOrNull { it.value() }!!
            player.hand.remove(chosen)
            return chosen
        }

        val hokm = engine.hokmSuit
        if (hokm != null && player.hand.any { it.suit == hokm }) {
            if (isTeammateLikelyWinner(player, engine)) {
                val chosen = playable.minByOrNull { it.value() }!!
                player.hand.remove(chosen)
                return chosen
            }
            val trumpsInHand = player.hand.filter { it.suit == hokm }
            val bestTrump = chooseMinimalWinningTrump(trumpsInHand, engine)
            if (bestTrump != null) {
                player.hand.remove(bestTrump)
                return bestTrump
            }
            val chosen = trumpsInHand.minByOrNull { it.value() } ?: playable.minByOrNull { it.value() }!!
            player.hand.remove(chosen)
            return chosen
        }

        val chosen = playable.minByOrNull { it.value() }!!
        player.hand.remove(chosen)
        return chosen
    }

    // Monte-Carlo based estimator: simulate random completion of trick/round many times and score team wins
    private fun estimateCardScore(player: Player, candidate: Card, engine: GameEngine, sims: Int): Double {
        var score = 0.0
        for (i in 0 until sims) {
            // make deep copy of engine state (lightweight)
            val simEngine = shallowCloneEngine(engine)
            // play candidate for player in sim
            simEngine.playCard(player.id, candidate.copy())
            // complete current trick with random legal plays
            completeTrickRandomly(simEngine)
            // simulate remaining plays randomly until round end and see which team wins
            val winner = simulateRandomPlayout(simEngine)
            // reward: +1 if player's team wins, else 0
            if (winner == player.team) score += 1.0
        }
        return score / sims
    }

    // shallow clone engine state (players' hands, playedCards, currentTrick, hokm)
    private fun shallowCloneEngine(engine: GameEngine): GameEngine {
        val g = GameEngine(engine.roundTarget)
        g.deck = engine.deck.toMutableList()
        g.hokmSuit = engine.hokmSuit
        g.dealerId = engine.dealerId
        g.teamAScore = engine.teamAScore
        g.teamBScore = engine.teamBScore
        g.playedCards.clear(); g.playedCards.addAll(engine.playedCards)
        g.currentTrick.clear(); g.currentTrick.addAll(engine.currentTrick.map { it.first to it.second.copy() })
        // copy players and their hands
        for (p in g.players) {
            val src = engine.players.first { it.id == p.id }
            p.hand.clear(); p.hand.addAll(src.hand.map { it.copy() })
        }
        return g
    }

    private fun completeTrickRandomly(engine: GameEngine) {
        while (engine.currentTrick.size < 4) {
            val nextPlayerIdx = engine.currentTrick.size
            val leadIndex = engine.players.indexOfFirst { it.id == engine.currentTrick.first().first }
            val p = engine.players[(leadIndex + nextPlayerIdx) % 4]
            val card = simulateChooseRandomLegal(p, engine)
            engine.playCard(p.id, card)
        }
        // resolve trick
        engine.resolveTrickAndReturnWinnerId()
    }

    fun simulateChooseRandomLegal(p: Player, engine: GameEngine): Card {
        val leadSuit = if (engine.currentTrick.isEmpty()) null else engine.currentTrick.first().second.suit
        val playable = if (leadSuit == null) p.hand.toList() else {
            val same = p.hand.filter { it.suit == leadSuit }
            if (same.isNotEmpty()) same else p.hand.toList()
        }
        val chosen = playable.random()
        p.hand.remove(chosen)
        return chosen
    }

    private fun simulateRandomPlayout(engine: GameEngine): Team {
        // simulate until one team has 7 tricks in this simulated round
        val tricksWon = mutableMapOf(Team.A to 0, Team.B to 0)
        // count playedCards to infer tricks already won roughly: but for simplicity we simulate fresh
        // complete remaining tricks randomly
        // ensure everyone has equalized hand sizes by continuing random plays
        while (engine.players.first().hand.isNotEmpty()) {
            // play a trick
            engine.currentTrick.clear()
            for (i in 0 until 4) {
                val p = engine.players[i]
                val card = simulateChooseRandomLegal(p, engine)
                engine.playCard(p.id, card)
            }
            val winnerId = engine.resolveTrickAndReturnWinnerId()
            val winner = engine.players.first { it.id == winnerId }
            tricksWon[winner.team] = tricksWon[winner.team]!! + 1
            if (tricksWon[Team.A]!! >= 7 || tricksWon[Team.B]!! >= 7) break
        }
        return if (tricksWon[Team.A]!! > tricksWon[Team.B]!!) Team.A else Team.B
    }

    private fun allHigherCardsPlayed(card: Card, engine: GameEngine): Boolean {
        val suit = card.suit
        val higher = fullRanksGreaterThan(card.rank)
        val played = engine.playedCards.filter { it.suit == suit }.map { it.rank }
        return higher.all { played.contains(it) }
    }

    private fun fullRanksGreaterThan(rank: String): List<String> {
        val order = listOf("2","3","4","5","6","7","8","9","10","J","Q","K","A")
        val idx = order.indexOf(rank)
        return if (idx == -1) emptyList() else order.subList(idx+1, order.size)
    }

    private fun isTeammateLikelyWinner(player: Player, engine: GameEngine): Boolean {
        if (engine.currentTrick.isEmpty()) return false
        val teammate = engine.players.first { it.team == player.team && it.id != player.id }
        val hokm = engine.hokmSuit
        if (hokm != null && engine.currentTrick.any { it.second.suit == hokm && it.first == teammate.id }) return true
        val currentWinnerId = engine.evaluateTrickWinnerIdPreview()
        return currentWinnerId == teammate.id
    }

    private fun chooseMinimalWinningTrump(trumps: List<Card>, engine: GameEngine): Card? {
        if (trumps.isEmpty()) return null
        val hokm = engine.hokmSuit ?: return null
        val trumpsPlayedThisTrick = engine.currentTrick.filter { it.second.suit == hokm }.map { it.second }
        val highestTrumpPlayed = trumpsPlayedThisTrick.maxByOrNull { it.value() }
        return if (highestTrumpPlayed == null) {
            trumps.minByOrNull { it.value() }
        } else {
            val candidates = trumps.filter { it.value() > highestTrumpPlayed.value() }
            if (candidates.isEmpty()) null else candidates.minByOrNull { it.value() }
        }
    }
}
