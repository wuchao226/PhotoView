package com.wuc.photoview;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;
import androidx.annotation.Nullable;

/**
 * @author : wuchao5
 * @date : 2021/12/16 14:57
 * @desciption :
 */
public class PhotoView extends View {
  private static final float IMAGE_WIDTH = Utils.dpToPixel(300);
  private Paint paint;
  private Bitmap mBitmap;
  /**
   * X轴偏移 这里主要用于图片初始化时设置居中
   */
  private float originalOffsetX;
  /**
   * Y轴偏移 这里主要用于图片初始化时设置居中
   */
  private float originalOffsetY;

  /**
   * 最小缩放比
   */
  private float smallScale;
  /**
   * 最大缩放比
   */
  private float bigScale;

  private float OVER_SCALE_FACTOR = 1.5f;
  /**
   * 当前缩放比
   */
  private float currentScale;
  /**
   * 手势操作
   */
  private GestureDetector mGestureDetector;
  /**
   * 缩放手势操作
   */
  private ScaleGestureDetector mScaleGestureDetector;
  /**
   * 处理惯性滑动
   */
  private OverScroller mOverScroller;
  /**
   * 是否已经放大
   */
  private boolean isEnlarge;
  /**
   * 拖动图片时X轴偏移量
   */
  private float offsetX;
  /**
   * 拖动图片时Y轴偏移量
   */
  private float offsetY;
  /**
   * 惯性滑动线程处理
   */
  private FlingRunner flingRunner;

  public PhotoView(Context context) {
    this(context, null);
  }

  public PhotoView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public PhotoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    // todo 步骤1 初始化
    // 获取bitmap对象
    // mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
    mBitmap = Utils.getPhoto(getResources(), (int) IMAGE_WIDTH);
    // 使位图抗锯齿
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // todo 步骤3 手势监听
    // 手势监听
    mGestureDetector = new GestureDetector(context, new PhotoGestureListener());
    mOverScroller = new OverScroller(context);
    flingRunner = new FlingRunner();
    // todo 步骤7 双指缩放监听
    // 双指缩放监听
    mScaleGestureDetector = new ScaleGestureDetector(context, new PhotoScaleGestureListener());
  }

  /**
   * onMeasure --> onSizeChanged
   * 在控件大小发生改变时调用 初始化时会被调用一次 后续控件大小变化时也会调用
   */
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // todo 步骤2 初始图片位置
    // 需要得到 浮点数，否则会留条小缝
    // 居中显示时 X、Y的值
    originalOffsetX = (getWidth() - mBitmap.getWidth()) / 2f;
    originalOffsetY = (getHeight() - mBitmap.getHeight()) / 2f;

    /*
     * 进入界面初始化图片时，需要满足左右两边填充整个屏幕或者上下两边填充整个屏幕
     * 所以要判断图片是竖形状的图片 还是横形状的图片
     * 这里用图片的宽高比与屏幕的宽高比进行对比来判断
     *
     * smallScale 表示图片缩放的比例  图片最小是多小
     * 命中 if: 图片按照宽度比进行缩放，当图片的宽度与屏幕的宽度相等时停止缩放，图片上下边界与屏幕上下边界会有间距
     * 命中 else: 图片按照高度比进行缩放，当图片的高度与屏幕的高度相等时停止缩放，图片左右边界与屏幕左右边界会有间距
     *
     * bigScale 表示图片缩放的比例  图片最大是多大
     * 不 *1.5f 的话，那么图片最大就是窄边与屏边对齐   *1.5f 表示图片放大后窄边也可以可以超出屏幕
     *
     * currentScale 的值到底是什么，取决于我们的需求
     * 在这里 smallScale 表示缩小  bigScale 表示放大
     * 当 currentScale = smallScale 时 双击图片之后 currentScale = bigScale  否则相反  这个判断在下面的双击事件里面处理
     */
    if ((float) mBitmap.getWidth() / mBitmap.getHeight() > (float) getWidth() / getHeight()) {
      smallScale = (float) getWidth() / mBitmap.getWidth();
      bigScale = (float) getHeight() / mBitmap.getHeight() * OVER_SCALE_FACTOR;
    } else {// 纵向的图片
      smallScale = (float) getHeight() / mBitmap.getHeight();
      bigScale = (float) getWidth() / mBitmap.getWidth() * OVER_SCALE_FACTOR;
    }
    currentScale = smallScale;
  }

  /**
   * 让某个 View 检测手势 - 重写 View 的 onTouch 函数，将 View 的触屏事件交给 GestureDetector 处理，从而对用户手势作出响应
   * 因为当 view 被点击的时候，进入的是 view 的 onTouchEvent 方法进行事件分发
   * 而我们这里用到的是 GestureDetector 所以 view 的 onTouchEvent 要托管给 GestureDetector 的 onTouchEvent 去执行
   * 但是同时 双指缩放的监听也需要用到S caleGestureDetector 的 onTouchEvent 所以还需要进行判断
   */
  @Override public boolean onTouchEvent(MotionEvent event) {
    // 响应事件以双指缩放优先
    boolean result = mScaleGestureDetector.onTouchEvent(event);
    // 判断 如果不是双指缩放 则把事件交给手势监听处理
    if (!mScaleGestureDetector.isInProgress()) {
      result = mGestureDetector.onTouchEvent(event);
    }
    return result;
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // todo 步骤9 处理图片放大平移后 再缩小时 图片不回到正中间的问题
    /*
     * 这个系数是如何来的可能有点绕
     * 我们需要解决步骤9的问题，首先需要偏移值 offsetX、offsetY 与我们的缩放因子进行绑定，缩放因子越大，偏移的值也越大
     * 我们知道，整个图片最大的缩放因子为 bigScale（终点），最小缩放因子为 smallScale（起点）， bigScale-smallScale 得到的就是总共缩放因子的区间值（距离）
     * 那么当前缩放因子是 currentScale（当前所在位置）， 减去 smallScale（起点），得到的是当前缩放因子距离最小缩放因子的值（当前位置-起点的位置）
     * 那么  （当前位置-起点的位置）/ 总距离   得到的就是距离比  也就是当前我完成了总路程的百分之几
     *
     * 结合下面的公式我们可以知道当 currentScale=smallScale 时 scaleAction 为0  此时图片不偏移
     * 当 currentScale=bigScale 时  scaleAction 为 1 此时的图片为最大图 那么这个时候如果移动图片的话 offsetX、offsetY 该偏移多少就偏移多少
     *
     * 所以当我们这样计算这个比例之后，当图片处于最大和最小之间时，我们手指平移100px，那么图片可能只会平移50px
     * 当图片处于最大的时候，我们手指平移100px，那么图片会平移100px
     * 当图片处于最小的时候，我们手指平移100px，那么图片会平移0px 也就是不平移
     * */
    float scaleAction = (currentScale - smallScale) / (bigScale - smallScale);
    /* 图片拖动的效果 */
    canvas.translate(offsetX * scaleAction, offsetY * scaleAction);
    // 缩放当前 Canvas 对象
    // smallScale --》 bigScale
    // 参数	说明
    // sx	水平方向缩放比例，小于 1 为缩小，大于 1 为放大
    // sy	竖直方向的缩放比例，小于 1 为缩小，大于 1 为放大
    // px	指定旋转的中心点坐标的 x 坐标
    // py	指定旋转的中心点坐标的 y 坐标
    /* 四个参数的意思分别是：图片X轴缩放系数、图片Y轴缩放系数、缩放时以哪个点进行缩放（我们取的是屏幕的中心点，默认是屏幕左上角，即0，0） */
    canvas.scale(currentScale, currentScale, getWidth() / 2f, getHeight() / 2f);
    // 绘制bitmap 居中显示
    canvas.drawBitmap(mBitmap, originalOffsetX, originalOffsetY, paint);
  }

  /**
   * 手势相关监听
   */
  class PhotoGestureListener extends GestureDetector.SimpleOnGestureListener {
    public PhotoGestureListener() {
      super();
    }

    /**
     * 用户轻击屏幕后抬起
     * 单击或者双击的第一次up时触发
     * 即如果不是长按、不是双击的第二次点击  则在up时触发
     */
    @Override public boolean onSingleTapUp(MotionEvent e) {
      return super.onSingleTapUp(e);
    }

    /** 长按触发 默认超过300ms时触发 */
    @Override public void onLongPress(MotionEvent e) {
      super.onLongPress(e);
    }

    /**
     * 滚动时（拖动图片）触发 -- move
     * 用户按下触摸屏 & 拖动
     *
     * @param e1 手指按下的事件
     * @param e2 当前的事件
     * @param distanceX 在 X 轴上滑过的距离（单位时间） 旧位置 - 新位置  所以小于 0 表示往右  大于 0 表示往左  所以计算偏移值时要取反
     * @param distanceY 同上
     */
    @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
      // todo 步骤5 处理拖拽
      // 当图片为放大模式时才允许拖动图片
      // distanceX 的值并不是起始位置与终点位置的差值，而是期间若干个小点汇聚而成的
      // 比如当从0px滑动到100px时，distanceX在1px时会出现，此时distanceX就是-1，然后可能又在2px时出现，
      // 在整个滑动过程中distanceX的值一直在变动，所以offsetX要一直计算   offsetY 同理
      if (isEnlarge) {
        offsetX -= distanceX;
        offsetY -= distanceY;
        // 计算图片可拖动的范围
        fixOffsets();
        invalidate();
      }
      return super.onScroll(e1, e2, distanceX, distanceY);
    }

    /**
     * 用户按下触摸屏、快速移动后松开
     * up时触发 手指拖动图片的时候 惯性滑动 -- 大于50dp/s
     *
     * @param e1 第1个ACTION_DOWN MotionEvent
     * @param e2 最后一个ACTION_MOVE MotionEvent
     * @param velocityX x轴方向运动速度（像素/s）
     * @param velocityY 同上
     */
    @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      // todo 步骤6 处理拖拽时的惯性滑动
      // 当图片为放大模式时才允许惯性滑动
      if (isEnlarge) {
        // 只会处理一次
        // 最后两个参数表示 当惯性滑动超出图片范围多少之后回弹，这也是为什么用 OverScroller 而不用 Scroller 的原因
        mOverScroller.fling((int) offsetX, (int) offsetY, (int) velocityX, (int) velocityY,
            -(int) (mBitmap.getWidth() * bigScale - getWidth()) / 2,
            (int) (mBitmap.getWidth() * bigScale - getWidth()) / 2,
            -(int) (mBitmap.getHeight() * bigScale - getHeight()) / 2,
            (int) (mBitmap.getHeight() * bigScale - getHeight()) / 2, 300, 300);
        postOnAnimation(flingRunner);
      }
      return super.onFling(e1, e2, velocityX, velocityY);
    }

    // 用户轻触触摸屏，尚未松开或拖动
    // 与onDown()的区别：无松开 / 拖动
    // 即：当用户点击的时，onDown（）就会执行，在按下的瞬间没有松开 / 拖动时onShowPress就会执行
    // 延时触发 100ms -- 点击效果，水波纹
    @Override public void onShowPress(MotionEvent e) {
      super.onShowPress(e);
    }

    /**
     * down 时触发 在这个需求里面，我们需要返回true 这与事件分发有关
     * 当返回 true 的时候下面的双击等函数才会执行 否则直接在这里就拦截了
     */
    @Override public boolean onDown(MotionEvent e) {
      return true;
    }

    /** 双击的第二次点击 down 时触发。双击的触发时间 -- 40ms -- 300ms */
    @Override public boolean onDoubleTap(MotionEvent e) {
      // todo 步骤4 处理双击
      if (!isEnlarge) {
        // isEnlarge 为false表示 取反前处于smallScale（缩小）状态，则双击后要变成bigScale（放大）
        // currentScale = bigScale;
        // 点击图片的哪个位置 哪个位置就进行放大 不设置的话 图片只会以中心进行放大
        // 其原理是以中心点先进行放大 再进行偏移
        offsetX = (e.getX() - getWidth() / 2f) - (e.getX() - getWidth() / 2f) * bigScale / smallScale;
        offsetY = (e.getY() - getHeight() / 2f) - (e.getY() - getHeight() / 2f) * bigScale / smallScale;
        // 计算图片可拖动的范围
        fixOffsets();
        // 这里直接添加动画， 图片从小到大  在 getScaleAnimator() 方法里面我们设置了 setFloatValues 的值
        getScaleAnimator(smallScale, bigScale).start();
      } else {
        // isFlag为true表示当前处于bigScale（放大）状态，则双击后要变成smallScale（缩小）
        // currentScale = smallScale;
        // 这里直接添加动画， 图片从大到小
        // 如果没有双指缩放功能，直接用下面这行代码就行了，但是在有双指缩放的情况下，如果图片被双指缩放到一半的时候再进行双击
        // 图片会有一个先缩小再放大的过程，这就是一个小BUG了
        // getAnimator(bigScale, smallScale).start();
        // 所以这里我们要用currentScale
        getScaleAnimator(currentScale, smallScale).start();
      }
      // 每次双击后取反
      isEnlarge = !isEnlarge;
      return super.onDoubleTap(e);
    }

    // 双击间隔中发生的动作
    // 指触发onDoubleTap后，在双击之间发生的其它动作，包含down、up和move事件；
    @Override public boolean onDoubleTapEvent(MotionEvent e) {
      return super.onDoubleTapEvent(e);
    }

    // OnDoubleTapListener的函数
    // 1. 单击事件
    // 关于OnDoubleTapListener.onSingleTapConfirmed（）和 OnGestureListener.onSingleTapUp()的区别
    // onSingleTapConfirmed：再次点击（即双击），则不会执行
    // onSingleTapUp：手抬起就会执行
    // 单击按下时触发，双击时不触发，
    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
      return super.onSingleTapConfirmed(e);
    }
  }

  private class FlingRunner implements Runnable {
    @Override public void run() {
      // 判断惯性动画是否结束 没结束返回true   结束了返回的是false
      if (mOverScroller.computeScrollOffset()) {
        offsetX = mOverScroller.getCurrX();
        offsetY = mOverScroller.getCurrY();
        invalidate();
        // 每帧动画执行一次，性能更好
        postOnAnimation(this);
      }
    }
  }

  /**
   * 允许拖动图片的边界
   *
   * 设置图片最大拖动的距离 如果不设置，那么拖动的距离超出图片之后，看到的就是白色的背景
   * 设置之后，当拖动图片到图片边界时，则不能继续往该方向拖了。
   */
  private void fixOffsets() {
    // 注意：offsetX 为拖动的距离，offsetX = -（旧位置-新位置）
    // (bitmap.getWidth()*bigScale - getWidth())/2 表示 图片宽度的一半 - 屏幕宽度的一半 得到的就是可以拖动的最大距离
    // 当图片往右时，我们的手也是往右 offsetX 为正数，图片可拖动的最大距离也为正数  此时取最小值为 offsetX
    // 例如我们手指往右滑动了 100px  而图片最大只能动 50px 再往右滑的话 图片的左边就超出图片范围了  因此取 50px
    offsetX = Math.min(offsetX, (mBitmap.getWidth() * bigScale - getWidth()) / 2);
    // 当图片往左时，我们的手也是往左 offsetX 为负数，而我们计算的图片可拖动的距离是正数 因此这里要取反 并且取两者最大值
    // 例如我们手指往左滑动了 100px 那么 offsetX = -100   而最大拖动距离为 50px 取反为 -50px   -100 与-50 取最大值
    offsetX = Math.max(offsetX, -(mBitmap.getWidth() * bigScale - getWidth()) / 2);
    // Y轴一样
    offsetY = Math.min(offsetY, (mBitmap.getHeight() * bigScale - getHeight()) / 2);
    offsetY = Math.max(offsetY, -(mBitmap.getHeight() * bigScale - getHeight()) / 2);
  }

  /**
   * 属性动画，设置放大缩小的效果
   */
  private ObjectAnimator scaleAnimator;

  private ObjectAnimator getScaleAnimator(float scale1, float scale2) {
    if (scaleAnimator == null) {
      // 这个方法内部是通过反射 设置 currentScale 的值  所以 currentScale 必须要有 get\set 方法
      scaleAnimator = ObjectAnimator.ofFloat(this, "currentScale", 0);
    }
    scaleAnimator.setFloatValues(scale1, scale2);
    return scaleAnimator;
  }

  public float getCurrentScale() {
    return currentScale;
  }

  /**
   * 属性动画，值会不断地从 smallScale 慢慢 加到 bigScale， 通过反射调用改方法
   */
  public void setCurrentScale(float currentScale) {
    this.currentScale = currentScale;
    // 由于属性动画 animator 会不断的调用 set 方法， 所以刷新放在这里
    invalidate();
  }

  /**
   * 双指缩放监听类
   */
  class PhotoScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

    float initialScale;

    /** 双指缩放时 */
    @Override public boolean onScale(ScaleGestureDetector detector) {
      // todo 步骤8 处理双指缩放以及缩放边距
      /*
       * detector.getScaleFactor() 表示两个手指之间缩放的大小值
       * 例如两个手指的距离缩短一半时 值为 0.5  两个手指距离增加一倍时  值为2
       *
       * initialScale 表示初始化时的缩放因子，currentScale 为最终的缩放因子
       * 这里不用 currentScale 直接乘的原因是 双指缩放的动作是持续性的，
       * 因此如果用 currentScale 直接乘的话 缩放因子基数会一直变动，这样取值不正确，
       * 正确的做法是要一直用缩放之前的因子 乘 detector.getScaleFactor()
       * */
      if ((currentScale >= bigScale && detector.getScaleFactor() < 1)
          || (currentScale <= smallScale && detector.getScaleFactor() > 1)
          || (currentScale > smallScale && currentScale < bigScale)) {

        if (initialScale * detector.getScaleFactor() <= smallScale) {
          // 解决双指缩放时超过图片最小边界
          currentScale = smallScale;
          isEnlarge = false;
        } else if (initialScale * detector.getScaleFactor() >= bigScale) {
          // 解决双指缩放时超过图片最大边界
          currentScale = bigScale;
          isEnlarge = true;
        } else {
          currentScale = initialScale * detector.getScaleFactor();
          isEnlarge = true;
        }
        invalidate();
      }
      return false;
    }

    /** 双指缩放之前 这里要return true  与我们事件分发一样的道理 */
    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
      initialScale = currentScale;
      return true;
    }

    /** 双指缩放之后 这里一般不做处理 */
    @Override public void onScaleEnd(ScaleGestureDetector detector) {

    }
  }

}