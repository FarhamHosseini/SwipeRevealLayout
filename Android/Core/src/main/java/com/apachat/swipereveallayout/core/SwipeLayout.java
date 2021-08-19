package com.apachat.swipereveallayout.core;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.apachat.swipereveallayout.core.interfaces.DragStateChanged;
import com.apachat.swipereveallayout.core.interfaces.Swipe;

public class SwipeLayout extends ViewGroup {
  protected static final int STATE_CLOSE = 0;
  protected static final int STATE_CLOSING = 1;
  protected static final int STATE_OPEN = 2;
  protected static final int STATE_OPENING = 3;
  protected static final int STATE_DRAGGING = 4;

  private static final int DEFAULT_MIN_FLING_VELOCITY = 300;
  private static final int DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1;

  public static final int DRAG_EDGE_LEFT = 0x1;
  public static final int DRAG_EDGE_RIGHT = 0x1 << 1;
  public static final int DRAG_EDGE_TOP = 0x1 << 2;
  public static final int DRAG_EDGE_BOTTOM = 0x1 << 3;

  public static final int MODE_NORMAL = 0;

  public static final int MODE_SAME_LEVEL = 1;

  private View mMainView;

  private View mSecondaryView;

  private Rect mRectMainClose = new Rect();

  private Rect mRectMainOpen = new Rect();

  private Rect mRectSecClose = new Rect();

  private Rect mRectSecOpen = new Rect();

  private int mMinDistRequestDisallowParent = 0;

  private boolean mIsOpenBeforeInit = false;
  private volatile boolean mAborted = false;
  private volatile boolean mIsScrolling = false;
  private volatile boolean mLockDrag = false;

  private int mMinFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;
  private int mState = STATE_CLOSE;
  private int mMode = MODE_NORMAL;

  private int mLastMainLeft = 0;
  private int mLastMainTop = 0;

  private int mDragEdge = DRAG_EDGE_LEFT;

  private float mDragDist = 0;
  private float mPrevX = -1;
  private float mPrevY = -1;

  private ViewDragHelper mDragHelper;
  private GestureDetectorCompat mGestureDetector;

  private DragStateChanged mDragStateChanged;
  private Swipe mSwipe;

  private int mOnLayoutCount = 0;

  public SwipeLayout(Context context) {
    super(context);
    init(context, null);
  }

  public SwipeLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public SwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    mGestureDetector.onTouchEvent(event);
    mDragHelper.processTouchEvent(event);
    return true;
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (isDragLocked()) {
      return super.onInterceptTouchEvent(ev);
    }

    mDragHelper.processTouchEvent(ev);
    mGestureDetector.onTouchEvent(ev);
    accumulateDragDist(ev);

    boolean couldBecomeClick = couldBecomeClick(ev);
    boolean settling = mDragHelper.getViewDragState() == ViewDragHelper.STATE_SETTLING;
    boolean idleAfterScrolled = mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE
      && mIsScrolling;

    mPrevX = ev.getX();
    mPrevY = ev.getY();

    return !couldBecomeClick && (settling || idleAfterScrolled);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    if (getChildCount() >= 2) {
      mSecondaryView = getChildAt(0);
      mMainView = getChildAt(1);
    } else if (getChildCount() == 1) {
      mMainView = getChildAt(0);
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    mAborted = false;

    for (int index = 0; index < getChildCount(); index++) {
      final View child = getChildAt(index);

      int left, right, top, bottom;
      left = right = top = bottom = 0;

      final int minLeft = getPaddingLeft();
      final int maxRight = Math.max(r - getPaddingRight() - l, 0);
      final int minTop = getPaddingTop();
      final int maxBottom = Math.max(b - getPaddingBottom() - t, 0);

      int measuredChildHeight = child.getMeasuredHeight();
      int measuredChildWidth = child.getMeasuredWidth();

      // need to take account if child size is match_parent
      final LayoutParams childParams = child.getLayoutParams();
      boolean matchParentHeight = false;
      boolean matchParentWidth = false;

      if (childParams != null) {
        matchParentHeight = (childParams.height == LayoutParams.MATCH_PARENT) ||
          (childParams.height == LayoutParams.FILL_PARENT);
        matchParentWidth = (childParams.width == LayoutParams.MATCH_PARENT) ||
          (childParams.width == LayoutParams.FILL_PARENT);
      }

      if (matchParentHeight) {
        measuredChildHeight = maxBottom - minTop;
        childParams.height = measuredChildHeight;
      }

      if (matchParentWidth) {
        measuredChildWidth = maxRight - minLeft;
        childParams.width = measuredChildWidth;
      }

      switch (mDragEdge) {
        case DRAG_EDGE_RIGHT:
          left = Math.max(r - measuredChildWidth - getPaddingRight() - l, minLeft);
          top = Math.min(getPaddingTop(), maxBottom);
          right = Math.max(r - getPaddingRight() - l, minLeft);
          bottom = Math.min(measuredChildHeight + getPaddingTop(), maxBottom);
          break;

        case DRAG_EDGE_LEFT:
          left = Math.min(getPaddingLeft(), maxRight);
          top = Math.min(getPaddingTop(), maxBottom);
          right = Math.min(measuredChildWidth + getPaddingLeft(), maxRight);
          bottom = Math.min(measuredChildHeight + getPaddingTop(), maxBottom);
          break;

        case DRAG_EDGE_TOP:
          left = Math.min(getPaddingLeft(), maxRight);
          top = Math.min(getPaddingTop(), maxBottom);
          right = Math.min(measuredChildWidth + getPaddingLeft(), maxRight);
          bottom = Math.min(measuredChildHeight + getPaddingTop(), maxBottom);
          break;

        case DRAG_EDGE_BOTTOM:
          left = Math.min(getPaddingLeft(), maxRight);
          top = Math.max(b - measuredChildHeight - getPaddingBottom() - t, minTop);
          right = Math.min(measuredChildWidth + getPaddingLeft(), maxRight);
          bottom = Math.max(b - getPaddingBottom() - t, minTop);
          break;
      }

      child.layout(left, top, right, bottom);
    }

    if (mMode == MODE_SAME_LEVEL) {
      switch (mDragEdge) {
        case DRAG_EDGE_LEFT:
          mSecondaryView.offsetLeftAndRight(-mSecondaryView.getWidth());
          break;

        case DRAG_EDGE_RIGHT:
          mSecondaryView.offsetLeftAndRight(mSecondaryView.getWidth());
          break;

        case DRAG_EDGE_TOP:
          mSecondaryView.offsetTopAndBottom(-mSecondaryView.getHeight());
          break;

        case DRAG_EDGE_BOTTOM:
          mSecondaryView.offsetTopAndBottom(mSecondaryView.getHeight());
      }
    }

    initRects();

    if (mIsOpenBeforeInit) {
      open(false);
    } else {
      close(false);
    }

    mLastMainLeft = mMainView.getLeft();
    mLastMainTop = mMainView.getTop();

    mOnLayoutCount++;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (getChildCount() < 2) {
      throw new RuntimeException("Layout must have two children");
    }

    final LayoutParams params = getLayoutParams();

    final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

    int desiredWidth = 0;
    int desiredHeight = 0;

    for (int i = 0; i < getChildCount(); i++) {
      final View child = getChildAt(i);
      measureChild(child, widthMeasureSpec, heightMeasureSpec);
      desiredWidth = Math.max(child.getMeasuredWidth(), desiredWidth);
      desiredHeight = Math.max(child.getMeasuredHeight(), desiredHeight);
    }

    widthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, widthMode);
    heightMeasureSpec = MeasureSpec.makeMeasureSpec(desiredHeight, heightMode);

    final int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
    final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);

    for (int i = 0; i < getChildCount(); i++) {
      final View child = getChildAt(i);
      final LayoutParams childParams = child.getLayoutParams();

      if (childParams != null) {
        if (childParams.height == LayoutParams.MATCH_PARENT) {
          child.setMinimumHeight(measuredHeight);
        }

        if (childParams.width == LayoutParams.MATCH_PARENT) {
          child.setMinimumWidth(measuredWidth);
        }
      }

      measureChild(child, widthMeasureSpec, heightMeasureSpec);
      desiredWidth = Math.max(child.getMeasuredWidth(), desiredWidth);
      desiredHeight = Math.max(child.getMeasuredHeight(), desiredHeight);
    }

    desiredWidth += getPaddingLeft() + getPaddingRight();
    desiredHeight += getPaddingTop() + getPaddingBottom();

    if (widthMode == MeasureSpec.EXACTLY) {
      desiredWidth = measuredWidth;
    } else {
      if (params.width == LayoutParams.MATCH_PARENT) {
        desiredWidth = measuredWidth;
      }

      if (widthMode == MeasureSpec.AT_MOST) {
        desiredWidth = (desiredWidth > measuredWidth) ? measuredWidth : desiredWidth;
      }
    }

    if (heightMode == MeasureSpec.EXACTLY) {
      desiredHeight = measuredHeight;
    } else {
      if (params.height == LayoutParams.MATCH_PARENT) {
        desiredHeight = measuredHeight;
      }

      if (heightMode == MeasureSpec.AT_MOST) {
        desiredHeight = (desiredHeight > measuredHeight) ? measuredHeight : desiredHeight;
      }
    }

    setMeasuredDimension(desiredWidth, desiredHeight);
  }

  @Override
  public void computeScroll() {
    if (mDragHelper.continueSettling(true)) {
      ViewCompat.postInvalidateOnAnimation(this);
    }
  }

  public void open(boolean animation) {
    mIsOpenBeforeInit = true;
    mAborted = false;

    if (animation) {
      mState = STATE_OPENING;
      mDragHelper.smoothSlideViewTo(mMainView, mRectMainOpen.left, mRectMainOpen.top);

      if (mDragStateChanged != null) {
        mDragStateChanged.onDragStateChanged(mState);
      }
    } else {
      mState = STATE_OPEN;
      mDragHelper.abort();

      mMainView.layout(
        mRectMainOpen.left,
        mRectMainOpen.top,
        mRectMainOpen.right,
        mRectMainOpen.bottom
      );

      mSecondaryView.layout(
        mRectSecOpen.left,
        mRectSecOpen.top,
        mRectSecOpen.right,
        mRectSecOpen.bottom
      );
    }

    ViewCompat.postInvalidateOnAnimation(SwipeLayout.this);
  }

  public void close(boolean animation) {
    mIsOpenBeforeInit = false;
    mAborted = false;

    if (animation) {
      mState = STATE_CLOSING;
      mDragHelper.smoothSlideViewTo(mMainView, mRectMainClose.left, mRectMainClose.top);

      if (mDragStateChanged != null) {
        mDragStateChanged.onDragStateChanged(mState);
      }

    } else {
      mState = STATE_CLOSE;
      mDragHelper.abort();

      mMainView.layout(
        mRectMainClose.left,
        mRectMainClose.top,
        mRectMainClose.right,
        mRectMainClose.bottom
      );

      mSecondaryView.layout(
        mRectSecClose.left,
        mRectSecClose.top,
        mRectSecClose.right,
        mRectSecClose.bottom
      );
    }

    ViewCompat.postInvalidateOnAnimation(SwipeLayout.this);
  }

  public void setMinFlingVelocity(int velocity) {
    mMinFlingVelocity = velocity;
  }

  public int getMinFlingVelocity() {
    return mMinFlingVelocity;
  }

  public void setDragEdge(int dragEdge) {
    mDragEdge = dragEdge;
  }

  public int getDragEdge() {
    return mDragEdge;
  }

  public void setSwipeListener(Swipe listener) {
    mSwipe = listener;
  }

  public void setLockDrag(boolean lock) {
    mLockDrag = lock;
  }

  public boolean isDragLocked() {
    return mLockDrag;
  }

  public boolean isOpened() {
    return (mState == STATE_OPEN);
  }

  public boolean isClosed() {
    return (mState == STATE_CLOSE);
  }

  void setDragStateChangeListener(DragStateChanged listener) {
    mDragStateChanged = listener;
  }

  protected void abort() {
    mAborted = true;
    mDragHelper.abort();
  }

  protected boolean shouldRequestLayout() {
    return mOnLayoutCount < 2;
  }

  private int getMainOpenLeft() {
    switch (mDragEdge) {
      case DRAG_EDGE_LEFT:
        return mRectMainClose.left + mSecondaryView.getWidth();

      case DRAG_EDGE_RIGHT:
        return mRectMainClose.left - mSecondaryView.getWidth();

      case DRAG_EDGE_TOP:
        return mRectMainClose.left;

      case DRAG_EDGE_BOTTOM:
        return mRectMainClose.left;

      default:
        return 0;
    }
  }

  private int getMainOpenTop() {
    switch (mDragEdge) {
      case DRAG_EDGE_LEFT:
        return mRectMainClose.top;

      case DRAG_EDGE_RIGHT:
        return mRectMainClose.top;

      case DRAG_EDGE_TOP:
        return mRectMainClose.top + mSecondaryView.getHeight();

      case DRAG_EDGE_BOTTOM:
        return mRectMainClose.top - mSecondaryView.getHeight();

      default:
        return 0;
    }
  }

  private int getSecOpenLeft() {
    if (mMode == MODE_NORMAL || mDragEdge == DRAG_EDGE_BOTTOM || mDragEdge == DRAG_EDGE_TOP) {
      return mRectSecClose.left;
    }

    if (mDragEdge == DRAG_EDGE_LEFT) {
      return mRectSecClose.left + mSecondaryView.getWidth();
    } else {
      return mRectSecClose.left - mSecondaryView.getWidth();
    }
  }

  private int getSecOpenTop() {
    if (mMode == MODE_NORMAL || mDragEdge == DRAG_EDGE_LEFT || mDragEdge == DRAG_EDGE_RIGHT) {
      return mRectSecClose.top;
    }

    if (mDragEdge == DRAG_EDGE_TOP) {
      return mRectSecClose.top + mSecondaryView.getHeight();
    } else {
      return mRectSecClose.top - mSecondaryView.getHeight();
    }
  }

  private void initRects() {
    mRectMainClose.set(
      mMainView.getLeft(),
      mMainView.getTop(),
      mMainView.getRight(),
      mMainView.getBottom()
    );

    mRectSecClose.set(
      mSecondaryView.getLeft(),
      mSecondaryView.getTop(),
      mSecondaryView.getRight(),
      mSecondaryView.getBottom()
    );

    mRectMainOpen.set(
      getMainOpenLeft(),
      getMainOpenTop(),
      getMainOpenLeft() + mMainView.getWidth(),
      getMainOpenTop() + mMainView.getHeight()
    );

    mRectSecOpen.set(
      getSecOpenLeft(),
      getSecOpenTop(),
      getSecOpenLeft() + mSecondaryView.getWidth(),
      getSecOpenTop() + mSecondaryView.getHeight()
    );
  }

  private boolean couldBecomeClick(MotionEvent ev) {
    return isInMainView(ev) && !shouldInitiateADrag();
  }

  private boolean isInMainView(@org.jetbrains.annotations.NotNull MotionEvent ev) {
    float x = ev.getX();
    float y = ev.getY();

    boolean withinVertical = mMainView.getTop() <= y && y <= mMainView.getBottom();
    boolean withinHorizontal = mMainView.getLeft() <= x && x <= mMainView.getRight();

    return withinVertical && withinHorizontal;
  }

  private boolean shouldInitiateADrag() {
    float minDistToInitiateDrag = mDragHelper.getTouchSlop();
    return mDragDist >= minDistToInitiateDrag;
  }

  private void accumulateDragDist(MotionEvent ev) {
    final int action = ev.getAction();
    if (action == MotionEvent.ACTION_DOWN) {
      mDragDist = 0;
      return;
    }

    boolean dragHorizontally = getDragEdge() == DRAG_EDGE_LEFT ||
      getDragEdge() == DRAG_EDGE_RIGHT;

    float dragged;
    if (dragHorizontally) {
      dragged = Math.abs(ev.getX() - mPrevX);
    } else {
      dragged = Math.abs(ev.getY() - mPrevY);
    }

    mDragDist += dragged;
  }

  private void init(Context context, AttributeSet attrs) {
    if (attrs != null && context != null) {
      TypedArray a = context.getTheme().obtainStyledAttributes(
        attrs,
        R.styleable.SwipeLayout,
        0, 0
      );

      mDragEdge = a.getInteger(R.styleable.SwipeLayout_dragEdge, DRAG_EDGE_LEFT);
      mMinFlingVelocity = a.getInteger(R.styleable.SwipeLayout_flingVelocity, DEFAULT_MIN_FLING_VELOCITY);
      mMode = a.getInteger(R.styleable.SwipeLayout_mode, MODE_NORMAL);

      mMinDistRequestDisallowParent = a.getDimensionPixelSize(
        R.styleable.SwipeLayout_minDistRequestDisallowParent,
        dpToPx(DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT)
      );
    }

    mDragHelper = ViewDragHelper.create(this, 1.0f, mDragHelperCallback);
    mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL);

    mGestureDetector = new GestureDetectorCompat(context, mGestureListener);
  }

  private final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
    boolean hasDisallowed = false;

    @Override
    public boolean onDown(MotionEvent e) {
      mIsScrolling = false;
      hasDisallowed = false;
      return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      mIsScrolling = true;
      return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
      mIsScrolling = true;

      if (getParent() != null) {
        boolean shouldDisallow;

        if (!hasDisallowed) {
          shouldDisallow = getDistToClosestEdge() >= mMinDistRequestDisallowParent;
          if (shouldDisallow) {
            hasDisallowed = true;
          }
        } else {
          shouldDisallow = true;
        }

        getParent().requestDisallowInterceptTouchEvent(shouldDisallow);
      }

      return false;
    }
  };

  private int getDistToClosestEdge() {
    switch (mDragEdge) {
      case DRAG_EDGE_LEFT:
        final int pivotRight = mRectMainClose.left + mSecondaryView.getWidth();

        return Math.min(
          mMainView.getLeft() - mRectMainClose.left,
          pivotRight - mMainView.getLeft()
        );

      case DRAG_EDGE_RIGHT:
        final int pivotLeft = mRectMainClose.right - mSecondaryView.getWidth();

        return Math.min(
          mMainView.getRight() - pivotLeft,
          mRectMainClose.right - mMainView.getRight()
        );

      case DRAG_EDGE_TOP:
        final int pivotBottom = mRectMainClose.top + mSecondaryView.getHeight();

        return Math.min(
          mMainView.getBottom() - pivotBottom,
          pivotBottom - mMainView.getTop()
        );

      case DRAG_EDGE_BOTTOM:
        final int pivotTop = mRectMainClose.bottom - mSecondaryView.getHeight();

        return Math.min(
          mRectMainClose.bottom - mMainView.getBottom(),
          mMainView.getBottom() - pivotTop
        );
    }

    return 0;
  }

  private int getHalfwayPivotHorizontal() {
    if (mDragEdge == DRAG_EDGE_LEFT) {
      return mRectMainClose.left + mSecondaryView.getWidth() / 2;
    } else {
      return mRectMainClose.right - mSecondaryView.getWidth() / 2;
    }
  }

  private int getHalfwayPivotVertical() {
    if (mDragEdge == DRAG_EDGE_TOP) {
      return mRectMainClose.top + mSecondaryView.getHeight() / 2;
    } else {
      return mRectMainClose.bottom - mSecondaryView.getHeight() / 2;
    }
  }

  private final ViewDragHelper.Callback mDragHelperCallback = new ViewDragHelper.Callback() {
    @Override
    public boolean tryCaptureView(View child, int pointerId) {
      mAborted = false;

      if (mLockDrag)
        return false;

      mDragHelper.captureChildView(mMainView, pointerId);
      return false;
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
      switch (mDragEdge) {
        case DRAG_EDGE_TOP:
          return Math.max(
            Math.min(top, mRectMainClose.top + mSecondaryView.getHeight()),
            mRectMainClose.top
          );

        case DRAG_EDGE_BOTTOM:
          return Math.max(
            Math.min(top, mRectMainClose.top),
            mRectMainClose.top - mSecondaryView.getHeight()
          );

        default:
          return child.getTop();
      }
    }

    @Override
    public int clampViewPositionHorizontal(View child, int left, int dx) {
      switch (mDragEdge) {
        case DRAG_EDGE_RIGHT:
          return Math.max(
            Math.min(left, mRectMainClose.left),
            mRectMainClose.left - mSecondaryView.getWidth()
          );

        case DRAG_EDGE_LEFT:
          return Math.max(
            Math.min(left, mRectMainClose.left + mSecondaryView.getWidth()),
            mRectMainClose.left
          );

        default:
          return child.getLeft();
      }
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
      final boolean velRightExceeded = pxToDp((int) xvel) >= mMinFlingVelocity;
      final boolean velLeftExceeded = pxToDp((int) xvel) <= -mMinFlingVelocity;
      final boolean velUpExceeded = pxToDp((int) yvel) <= -mMinFlingVelocity;
      final boolean velDownExceeded = pxToDp((int) yvel) >= mMinFlingVelocity;

      final int pivotHorizontal = getHalfwayPivotHorizontal();
      final int pivotVertical = getHalfwayPivotVertical();

      switch (mDragEdge) {
        case DRAG_EDGE_RIGHT:
          if (velRightExceeded) {
            close(true);
          } else if (velLeftExceeded) {
            open(true);
          } else {
            if (mMainView.getRight() < pivotHorizontal) {
              open(true);
            } else {
              close(true);
            }
          }
          break;

        case DRAG_EDGE_LEFT:
          if (velRightExceeded) {
            open(true);
          } else if (velLeftExceeded) {
            close(true);
          } else {
            if (mMainView.getLeft() < pivotHorizontal) {
              close(true);
            } else {
              open(true);
            }
          }
          break;

        case DRAG_EDGE_TOP:
          if (velUpExceeded) {
            close(true);
          } else if (velDownExceeded) {
            open(true);
          } else {
            if (mMainView.getTop() < pivotVertical) {
              close(true);
            } else {
              open(true);
            }
          }
          break;

        case DRAG_EDGE_BOTTOM:
          if (velUpExceeded) {
            open(true);
          } else if (velDownExceeded) {
            close(true);
          } else {
            if (mMainView.getBottom() < pivotVertical) {
              open(true);
            } else {
              close(true);
            }
          }
          break;
      }
    }

    @Override
    public void onEdgeDragStarted(int edgeFlags, int pointerId) {
      super.onEdgeDragStarted(edgeFlags, pointerId);

      if (mLockDrag) {
        return;
      }

      boolean edgeStartLeft = (mDragEdge == DRAG_EDGE_RIGHT)
        && edgeFlags == ViewDragHelper.EDGE_LEFT;

      boolean edgeStartRight = (mDragEdge == DRAG_EDGE_LEFT)
        && edgeFlags == ViewDragHelper.EDGE_RIGHT;

      boolean edgeStartTop = (mDragEdge == DRAG_EDGE_BOTTOM)
        && edgeFlags == ViewDragHelper.EDGE_TOP;

      boolean edgeStartBottom = (mDragEdge == DRAG_EDGE_TOP)
        && edgeFlags == ViewDragHelper.EDGE_BOTTOM;

      if (edgeStartLeft || edgeStartRight || edgeStartTop || edgeStartBottom) {
        mDragHelper.captureChildView(mMainView, pointerId);
      }
    }

    @Override
    public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
      super.onViewPositionChanged(changedView, left, top, dx, dy);
      if (mMode == MODE_SAME_LEVEL) {
        if (mDragEdge == DRAG_EDGE_LEFT || mDragEdge == DRAG_EDGE_RIGHT) {
          mSecondaryView.offsetLeftAndRight(dx);
        } else {
          mSecondaryView.offsetTopAndBottom(dy);
        }
      }

      boolean isMoved = (mMainView.getLeft() != mLastMainLeft) || (mMainView.getTop() != mLastMainTop);
      if (mSwipe != null && isMoved) {
        if (mMainView.getLeft() == mRectMainClose.left && mMainView.getTop() == mRectMainClose.top) {
          mSwipe.onClosed(SwipeLayout.this);
        } else if (mMainView.getLeft() == mRectMainOpen.left && mMainView.getTop() == mRectMainOpen.top) {
          mSwipe.onOpened(SwipeLayout.this);
        } else {
          mSwipe.onSlide(SwipeLayout.this, getSlideOffset());
        }
      }

      mLastMainLeft = mMainView.getLeft();
      mLastMainTop = mMainView.getTop();
      ViewCompat.postInvalidateOnAnimation(SwipeLayout.this);
    }

    private float getSlideOffset() {
      switch (mDragEdge) {
        case DRAG_EDGE_LEFT:
          return (float) (mMainView.getLeft() - mRectMainClose.left) / mSecondaryView.getWidth();

        case DRAG_EDGE_RIGHT:
          return (float) (mRectMainClose.left - mMainView.getLeft()) / mSecondaryView.getWidth();

        case DRAG_EDGE_TOP:
          return (float) (mMainView.getTop() - mRectMainClose.top) / mSecondaryView.getHeight();

        case DRAG_EDGE_BOTTOM:
          return (float) (mRectMainClose.top - mMainView.getTop()) / mSecondaryView.getHeight();

        default:
          return 0;
      }
    }

    @Override
    public void onViewDragStateChanged(int state) {
      super.onViewDragStateChanged(state);
      final int prevState = mState;

      switch (state) {
        case ViewDragHelper.STATE_DRAGGING:
          mState = STATE_DRAGGING;
          break;

        case ViewDragHelper.STATE_IDLE:

          // drag edge is left or right
          if (mDragEdge == DRAG_EDGE_LEFT || mDragEdge == DRAG_EDGE_RIGHT) {
            if (mMainView.getLeft() == mRectMainClose.left) {
              mState = STATE_CLOSE;
            } else {
              mState = STATE_OPEN;
            }
          }

          // drag edge is top or bottom
          else {
            if (mMainView.getTop() == mRectMainClose.top) {
              mState = STATE_CLOSE;
            } else {
              mState = STATE_OPEN;
            }
          }
          break;
      }

      if (mDragStateChanged != null && !mAborted && prevState != mState) {
        mDragStateChanged.onDragStateChanged(mState);
      }
    }
  };

  public static String getStateString(int state) {
    switch (state) {
      case STATE_CLOSE:
        return "state_close";

      case STATE_CLOSING:
        return "state_closing";

      case STATE_OPEN:
        return "state_open";

      case STATE_OPENING:
        return "state_opening";

      case STATE_DRAGGING:
        return "state_dragging";

      default:
        return "undefined";
    }
  }

  private int pxToDp(int px) {
    Resources resources = getContext().getResources();
    DisplayMetrics metrics = resources.getDisplayMetrics();
    return (int) (px / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
  }

  private int dpToPx(int dp) {
    Resources resources = getContext().getResources();
    DisplayMetrics metrics = resources.getDisplayMetrics();
    return (int) (dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
  }

}
