package co.edu.unal.tictactoe;

import java.util.Random;

public class TicTacToeGame {

    public static final int BOARD_SIZE = 9;

    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';
    public static final char OPEN_SPOT = ' ';

    private final char[] mBoard = new char[BOARD_SIZE];
    private final Random mRand;

    public TicTacToeGame() {
        mRand = new Random();
        clearBoard();
    }

    /**
     * Limpia el tablero colocando todas las posiciones como disponibles.
     */
    public void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            mBoard[i] = OPEN_SPOT;
        }
    }

    /**
     * Coloca una X u O en una posición disponible.
     *
     * @param player HUMAN_PLAYER o COMPUTER_PLAYER
     * @param location posición del tablero entre 0 y 8
     */
    public void setMove(char player, int location) {
        if (location >= 0 && location < BOARD_SIZE && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player;
        }
    }


    /**
     * Retorna la mejor posición para el movimiento del computador.
     * No modifica definitivamente el tablero.
     *
     * @return posición entre 0 y 8
     */
    public int getComputerMove() {

        // 1. Buscar una jugada que permita ganar
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = COMPUTER_PLAYER;

                if (checkForWinner() == 3) {
                    mBoard[i] = OPEN_SPOT;
                    return i;
                }

                mBoard[i] = OPEN_SPOT;
            }
        }

        // 2. Buscar una jugada para bloquear al jugador
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = HUMAN_PLAYER;

                if (checkForWinner() == 2) {
                    mBoard[i] = OPEN_SPOT;
                    return i;
                }

                mBoard[i] = OPEN_SPOT;
            }
        }

        // 3. Escoger aleatoriamente una posición disponible
        int move;

        do {
            move = mRand.nextInt(BOARD_SIZE);
        } while (mBoard[move] != OPEN_SPOT);

        return move;
    }

    /**
     * Verifica el estado de la partida.
     *
     * @return
     * 0 = juego continúa
     * 1 = empate
     * 2 = gana jugador
     * 3 = gana computador
     */
    public int checkForWinner() {

        // Filas
        for (int i = 0; i <= 6; i += 3) {
            if (mBoard[i] == HUMAN_PLAYER &&
                    mBoard[i + 1] == HUMAN_PLAYER &&
                    mBoard[i + 2] == HUMAN_PLAYER) {
                return 2;
            }

            if (mBoard[i] == COMPUTER_PLAYER &&
                    mBoard[i + 1] == COMPUTER_PLAYER &&
                    mBoard[i + 2] == COMPUTER_PLAYER) {
                return 3;
            }
        }

        // Columnas
        for (int i = 0; i <= 2; i++) {
            if (mBoard[i] == HUMAN_PLAYER &&
                    mBoard[i + 3] == HUMAN_PLAYER &&
                    mBoard[i + 6] == HUMAN_PLAYER) {
                return 2;
            }

            if (mBoard[i] == COMPUTER_PLAYER &&
                    mBoard[i + 3] == COMPUTER_PLAYER &&
                    mBoard[i + 6] == COMPUTER_PLAYER) {
                return 3;
            }
        }

        // Diagonales
        if ((mBoard[0] == HUMAN_PLAYER &&
                mBoard[4] == HUMAN_PLAYER &&
                mBoard[8] == HUMAN_PLAYER) ||
                (mBoard[2] == HUMAN_PLAYER &&
                        mBoard[4] == HUMAN_PLAYER &&
                        mBoard[6] == HUMAN_PLAYER)) {
            return 2;
        }

        if ((mBoard[0] == COMPUTER_PLAYER &&
                mBoard[4] == COMPUTER_PLAYER &&
                mBoard[8] == COMPUTER_PLAYER) ||
                (mBoard[2] == COMPUTER_PLAYER &&
                        mBoard[4] == COMPUTER_PLAYER &&
                        mBoard[6] == COMPUTER_PLAYER)) {
            return 3;
        }

        // Comprobar si aún existen posiciones libres
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                return 0;
            }
        }

        return 1;
    }
}
