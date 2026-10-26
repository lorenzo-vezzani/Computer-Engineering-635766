from collections import Counter
from itertools import combinations
import random

class PokerState:
    def __init__(self, hand, board):
        self.hand = hand
        self.board = board

class PokerEngine:

    RAISE_THRESHOLD = 0.75
    CALL_THRESHOLD = 0.45
    
    RANKS = "23456789TJQKA"
    SUITS = "CDHS"
    RANK_VALUE = {r: i for i, r in enumerate(RANKS)}

    # computes the value of a 5-card poker hand.
    #
    # the hand strength is encoded as a tuple so that two hands can be
    # compared directly using Python's lexicographic tuple comparison.
    #
    # tuple formats:
    #   (8, straight_high)                    -> Straight Flush
    #   (7, four_rank, kicker)                -> Poker
    #   (6, three_rank, pair_rank)            -> Full
    #   (5, r1, r2, r3, r4, r5)               -> Flush
    #   (4, straight_high)                    -> Straight
    #   (3, three_rank, k1, k2)               -> Tris
    #   (2, high_pair, low_pair, kicker)      -> Two Pair
    #   (1, pair_rank, k1, k2, k3)            -> One Pair
    #   (0, r1, r2, r3, r4, r5)               -> High Card
    #
    # the first element identifies the hand category (higher is better).
    # the remaining elements are tie-breakers ordered by significance.
    @staticmethod
    def hand_value(hand):
        ranks = sorted([PokerEngine.RANK_VALUE[c[0]] for c in hand], reverse=True)
        suits = [c[1] for c in hand]
        count = Counter(ranks)
        counts = sorted(count.values(), reverse=True)
        is_flush = len(set(suits)) == 1
        is_straight = False
        unique_ranks = sorted(set(ranks))
        
        if unique_ranks == [0, 1, 2, 3, 12] or unique_ranks == [12, 3, 2, 1, 0]:
            is_straight = True
            straight_high = 3
        else:
            if len(unique_ranks) == 5 and unique_ranks[4] - unique_ranks[0] == 4:
                is_straight = True
                straight_high = unique_ranks[4]
    
        if is_straight and is_flush: return (8, straight_high)
        if counts == [4, 1]:
            four = count.most_common(1)[0][0]
            kicker = [r for r in ranks if r != four][0]
            return (7, four, kicker)
        if counts == [3, 2]:
            three = count.most_common(1)[0][0]
            pair = [r for r, c in count.items() if c == 2][0]
            return (6, three, pair)
        if is_flush: return (5, *ranks)
        if is_straight: return (4, straight_high)
        if counts == [3, 1, 1]:
            three = count.most_common(1)[0][0]
            kickers = sorted([r for r in ranks if r != three], reverse=True)
            return (3, three, *kickers)
        if counts == [2, 2, 1]:
            pairs = [r for r, c in count.items() if c == 2]
            pairs = sorted(pairs, reverse=True)
            kicker = [r for r in ranks if count[r] == 1][0]
            return (2, *pairs, kicker)
        if counts == [2, 1, 1, 1]:
            pair = count.most_common(1)[0][0]
            kickers = sorted([r for r in ranks if r != pair], reverse=True)
            return (1, pair, *kickers)
        return (0, *ranks)

    # computes the value of the player's hand trying all the possibile 21 combinations
    @staticmethod
    def best_5(hand, board):
        all_cards = list(hand) + list(board)
        all_combos = list(combinations(all_cards, 5))
        return max(all_combos, key=PokerEngine.hand_value)

    # applies montecarlo method to calculate the odds of victory/tie/defeat of the player
    # montecarlo will be applied "iterations" times
    # if the board is not completed, it will be randomly completed
    @classmethod
    def calculate_odds(cls, state, n_opponents, iterations): 
        deck = [r + s for r in cls.RANKS for s in cls.SUITS]
        used_cards = set(state.hand + state.board)
        deck = [c for c in deck if c not in used_cards]
    
        wins, ties, losses = 0, 0, 0
    
        for _ in range(iterations):
            available = deck.copy()
            random.shuffle(available)
    
            simulated_board = state.board.copy()
            missing_board = 5 - len(simulated_board)
            simulated_board.extend(available[:missing_board])
            available = available[missing_board:]
            
            opponents = []
            for _ in range(n_opponents):
                opponents.append([available.pop(), available.pop()])
    
            hero_best = cls.best_5(state.hand, simulated_board)
            hero_score = cls.hand_value(hero_best)
    
            hero_wins = True
            hero_tie = False
    
            for hand in opponents:
                opp_best = cls.best_5(hand, simulated_board)
                opp_score = cls.hand_value(opp_best)
                
                if opp_score > hero_score:
                    hero_wins = False
                    break
                elif opp_score == hero_score:
                    hero_tie = True
    
            if hero_wins:
                if hero_tie:
                    ties += 1
                else:
                    wins += 1
            else:
                losses += 1
    
        return {
            "win": wins / iterations,
            "tie": ties / iterations,
            "lose": losses / iterations
        }

    @classmethod
    def suggest_action(cls, state, n_opponents=3, iterations=10000):
        odds = cls.calculate_odds(state, n_opponents, iterations)
        win_rate = odds["win"]
        
        print(f"Odds -> Win: {win_rate:.2%}, Tie: {odds['tie']:.2%}, Loss: {odds['lose']:.2%}")

        if win_rate > cls.RAISE_THRESHOLD:
            return "RAISE"
        if win_rate > cls.CALL_THRESHOLD:
            return "CALL"
        return "FOLD"

    @classmethod
    def redefine_threshold(cls, _raise, _call):
        cls.RAISE_THRESHOLD = _raise
        cls.CALL_THRESHOLD = _call
        