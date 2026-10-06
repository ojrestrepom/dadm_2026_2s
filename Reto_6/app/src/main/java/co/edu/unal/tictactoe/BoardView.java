package co.edu.unal.tictactoe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.List;

/** Dibuja el tablero; las reglas y las jugadas pertenecen al modelo y la actividad. */
public class BoardView extends View {

    public interface OnCellSelectedListener {
        void onCellSelected(int location);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF mCellBounds = new RectF();
    private final Bitmap mHumanBitmap;
    private final Bitmap mComputerBitmap;
    private final BoardAccessibilityHelper mAccessibilityHelper;
    private final int mTouchSlop;
    private TicTacToeGame mGame;
    private OnCellSelectedListener mListener;
    private int mPressedCell = -1;
    private float mDownX;
    private float mDownY;

    public BoardView(Context context) {
        this(context, null);
    }

    public BoardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHumanBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.x_img);
        mComputerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.o_img);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAccessibilityHelper = new BoardAccessibilityHelper();
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setGame(TicTacToeGame game) {
        mGame = game;
        refreshBoard();
    }

    public void setOnCellSelectedListener(OnCellSelectedListener listener) {
        mListener = listener;
    }

    public void refreshBoard() {
        mPressedCell = -1;
        invalidate();
        mAccessibilityHelper.invalidateRoot();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = Math.round(dp(288));
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int side = Math.min(width, height);
        setMeasuredDimension(
                MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ? width : side,
                MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY ? height : side);
    }

    private float boardSize() {
        return Math.max(0, Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom()));
    }

    private float boardLeft() {
        return getPaddingLeft()
                + (getWidth() - getPaddingLeft() - getPaddingRight() - boardSize()) / 2f;
    }

    private float boardTop() {
        return getPaddingTop()
                + (getHeight() - getPaddingTop() - getPaddingBottom() - boardSize()) / 2f;
    }

    private void cellBounds(int cell, RectF bounds) {
        float size = boardSize() / 3f;
        float left = boardLeft() + (cell % 3) * size;
        float top = boardTop() + (cell / 3) * size;
        bounds.set(left, top, left + size, top + size);
    }

    private int cellAt(float x, float y) {
        float left = boardLeft();
        float top = boardTop();
        float size = boardSize();
        if (size <= 0 || x < left || y < top || x >= left + size || y >= top + size) {
            return -1;
        }
        return (int) ((y - top) * 3 / size) * 3 + (int) ((x - left) * 3 / size);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float size = boardSize();
        float left = boardLeft();
        float top = boardTop();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(3));
        mPaint.setColor(Color.LTGRAY);
        for (int i = 1; i < 3; i++) {
            float offset = i * size / 3f;
            canvas.drawLine(left + offset, top, left + offset, top + size, mPaint);
            canvas.drawLine(left, top + offset, left + size, top + offset, mPaint);
        }
        if (mGame != null) {
            for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
                char occupant = mGame.getBoardOccupant(i);
                Bitmap bitmap = occupant == TicTacToeGame.HUMAN_PLAYER ? mHumanBitmap
                        : occupant == TicTacToeGame.COMPUTER_PLAYER ? mComputerBitmap : null;
                if (bitmap != null) {
                    cellBounds(i, mCellBounds);
                    mCellBounds.inset(size / 30f, size / 30f);
                    canvas.drawBitmap(bitmap, null, mCellBounds, mPaint);
                }
            }
        }
        int focused = mAccessibilityHelper.getKeyboardFocusedVirtualViewId();
        if (focused >= 0 && focused < TicTacToeGame.BOARD_SIZE) {
            cellBounds(focused, mCellBounds);
            mCellBounds.inset(dp(3), dp(3));
            mPaint.setColor(Color.DKGRAY);
            mPaint.setStrokeWidth(dp(2));
            canvas.drawRect(mCellBounds, mPaint);
        }
    }

    private boolean canSelect(int cell) {
        return isEnabled() && mListener != null && mGame != null
                && cell >= 0 && cell < TicTacToeGame.BOARD_SIZE
                && mGame.checkForWinner() == 0
                && mGame.getBoardOccupant(cell) == TicTacToeGame.OPEN_SPOT;
    }

    private boolean selectCell(int cell) {
        if (!canSelect(cell)) return false;
        mListener.onCellSelected(cell);
        mAccessibilityHelper.sendEventForVirtualView(cell, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressedCell = cellAt(event.getX(), event.getY());
                mDownX = event.getX();
                mDownY = event.getY();
                return mPressedCell >= 0;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - mDownX) > mTouchSlop
                        || Math.abs(event.getY() - mDownY) > mTouchSlop) {
                    mPressedCell = -1;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mPressedCell >= 0 && mPressedCell == cellAt(event.getX(), event.getY())) {
                    performClick();
                }
                mPressedCell = -1;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                mPressedCell = -1;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return selectCell(mPressedCell);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return mAccessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mAccessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        mAccessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private class BoardAccessibilityHelper extends ExploreByTouchHelper {
        BoardAccessibilityHelper() {
            super(BoardView.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            int cell = cellAt(x, y);
            return cell < 0 ? INVALID_ID : cell;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) virtualViewIds.add(i);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int cell,
                @NonNull AccessibilityNodeInfoCompat node) {
            char occupant = mGame == null ? TicTacToeGame.OPEN_SPOT : mGame.getBoardOccupant(cell);
            int label = occupant == TicTacToeGame.HUMAN_PLAYER ? R.string.board_cell_human
                    : occupant == TicTacToeGame.COMPUTER_PLAYER ? R.string.board_cell_computer
                    : R.string.board_cell_empty;
            node.setContentDescription(getResources().getString(R.string.board_cell_description,
                    cell / 3 + 1, cell % 3 + 1, getResources().getString(label)));
            node.setClassName("android.widget.Button");
            node.setEnabled(canSelect(cell));
            node.setClickable(canSelect(cell));
            if (canSelect(cell)) {
                node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
            }
            RectF bounds = new RectF();
            cellBounds(cell, bounds);
            Rect rect = new Rect();
            bounds.roundOut(rect);
            setBoundsInScreenFromBoundsInParent(node, rect);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int cell, int action, Bundle arguments) {
            return action == AccessibilityNodeInfoCompat.ACTION_CLICK && selectCell(cell);
        }

        @Override
        protected void onVirtualViewKeyboardFocusChanged(int cell, boolean hasFocus) {
            invalidate();
        }
    }
}