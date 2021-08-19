package com.apachat.swipereveallayout.core.interfaces;

import com.apachat.swipereveallayout.core.SwipeLayout;

public interface Swipe {
  void onClosed(SwipeLayout view);

  void onOpened(SwipeLayout view);

  void onSlide(SwipeLayout view, float slideOffset);
}