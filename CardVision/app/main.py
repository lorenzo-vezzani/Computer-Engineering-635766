import os
from detector import CardDetector
from poker import PokerEngine, PokerState

# used to check if there are duplicates in hand and/or board
def check_duplicate_cards(hand_cards, board_cards):
    all_cards = hand_cards + board_cards

    if len(set(all_cards)) != len(all_cards):
        return True

    return False

def get_existing_image(prompt, required=True):
    while True:
        path = input(prompt).strip()

        if not path:
            if required:
                print("You must provide an image path.")
                continue
            return None

        if os.path.isfile(path):
            return path

        print("File not found. Please try again.")


def main():
    detector = CardDetector()

    # the player's hand is mandatory and its length must be exactly 2
    while True:
        hand_image = get_existing_image("Hand image path: ")
        print("\nHAND IMAGE DETECTION")
        hand_cards = detector.detect(hand_image)

        if len(hand_cards) == 2:
            break

        print(
            f"Error: exactly 2 cards must be detected in the hand image "
            f"(detected {len(hand_cards)}). Please try again.\n"
        )

    # the board is not mandatory and its length must be in [3, 4, 5]
    while True:
        board_image = get_existing_image(
            "Board image path (press Enter if not available): ",
            required=False
        )

        if board_image is None:
            board_cards = []
            break

        print("\nBOARD IMAGE DETECTION")
        board_cards = detector.detect(board_image)

        if len(board_cards) in {3, 4, 5}:
            break

        print(
            f"Error: the board image must contain exactly 3, 4 or 5 cards "
            f"(detected {len(board_cards)}). Please try again.\n"
        )

    if check_duplicate_cards(hand_cards, board_cards):
        print("Error: duplicate cards detected between hand and board.")
        exit(1)
    
    print(f"\nHAND CARDS: {hand_cards}")
    print(f"BOARD CARDS: {board_cards}")

    current_state = PokerState(hand_cards, board_cards)

    # number of opponents is required (must be positive)
    while True:
        n_opponents = int(input("Number of opponents: "))
        if(n_opponents > 0):
            break

        print(f"Number of opponents must be greater than 0. Please try again.")

    # if number of iterations is defined, it must be positive
    # otherwise it will be set at his default value 10k
    iterations = input("Number of iterations (press Enter for default value 10k): ")
    iterations = int(iterations) if iterations and iterations > 0 else 10000
    
    raise_threshold = input("Select threshold for raise suggestion (press Enter for default value 0.75): ")
    raise_threshold = float(raise_threshold) if raise_threshold and 0 < float(raise_threshold) < 1 else 0.75
    
    call_threshold = input("Select threshold for call suggestion (press Enter for default value 0.45): ")
    call_threshold = float(call_threshold) if call_threshold and 0 < float(call_threshold) < 1 else 0.45
    
    PokerEngine.redefine_threshold(raise_threshold, call_threshold)

    action = PokerEngine.suggest_action(current_state, n_opponents, iterations)
    print(f"\nSuggested action: {action}")


if __name__ == "__main__":
    main()
    