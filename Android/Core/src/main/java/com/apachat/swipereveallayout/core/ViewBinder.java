package com.apachat.swipereveallayout.core;

import android.os.Bundle;

import com.apachat.swipereveallayout.core.interfaces.DragStateChanged;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ViewBinder {
  private static final String BUNDLE_MAP_KEY = "ViewBinder_BUNDLE_MAP_KEY";

  private Map<String, Integer> mapStates = Collections.synchronizedMap(new HashMap<String, Integer>());
  private Map<String, SwipeLayout> mapLayouts = Collections.synchronizedMap(new HashMap<String, SwipeLayout>());
  private Set<String> lockedSwipeSet = Collections.synchronizedSet(new HashSet<String>());

  private volatile boolean openOnlyOne = false;
  private final Object stateChangeLock = new Object();

  public void bind(final SwipeLayout swipeLayout, final String id) {
    if (swipeLayout.shouldRequestLayout()) {
      swipeLayout.requestLayout();
    }

    mapLayouts.values().remove(swipeLayout);
    mapLayouts.put(id, swipeLayout);

    swipeLayout.abort();
    swipeLayout.setDragStateChangeListener(new DragStateChanged() {
      @Override
      public void onDragStateChanged(int state) {
        mapStates.put(id, state);

        if (openOnlyOne) {
          closeOthers(id, swipeLayout);
        }
      }
    });

    if (!mapStates.containsKey(id)) {
      mapStates.put(id, SwipeLayout.STATE_CLOSE);
      swipeLayout.close(false);
    } else {
      int state = mapStates.get(id);
      if (state == SwipeLayout.STATE_CLOSE || state == SwipeLayout.STATE_CLOSING ||
        state == SwipeLayout.STATE_DRAGGING) {
        swipeLayout.close(false);
      } else {
        swipeLayout.open(false);
      }
    }

    swipeLayout.setLockDrag(lockedSwipeSet.contains(id));
  }

  public void saveStates(Bundle outState) {
    if (outState == null)
      return;

    Bundle statesBundle = new Bundle();
    for (Map.Entry<String, Integer> entry : mapStates.entrySet()) {
      statesBundle.putInt(entry.getKey(), entry.getValue());
    }

    outState.putBundle(BUNDLE_MAP_KEY, statesBundle);
  }

  public void restoreStates(Bundle inState) {
    if (inState == null)
      return;

    if (inState.containsKey(BUNDLE_MAP_KEY)) {
      HashMap<String, Integer> restoredMap = new HashMap<>();

      Bundle statesBundle = inState.getBundle(BUNDLE_MAP_KEY);
      Set<String> keySet = statesBundle.keySet();

      if (keySet != null) {
        for (String key : keySet) {
          restoredMap.put(key, statesBundle.getInt(key));
        }
      }

      mapStates = restoredMap;
    }
  }

  public void lockSwipe(String... id) {
    setLockSwipe(true, id);
  }

  public void unlockSwipe(String... id) {
    setLockSwipe(false, id);
  }

  public void setOpenOnlyOne(boolean openOnlyOne) {
    this.openOnlyOne = openOnlyOne;
  }

  public void openLayout(final String id) {
    synchronized (stateChangeLock) {
      mapStates.put(id, SwipeLayout.STATE_OPEN);

      if (mapLayouts.containsKey(id)) {
        final SwipeLayout layout = mapLayouts.get(id);
        layout.open(true);
      } else if (openOnlyOne) {
        closeOthers(id, mapLayouts.get(id));
      }
    }
  }

  public void closeLayout(final String id) {
    synchronized (stateChangeLock) {
      mapStates.put(id, SwipeLayout.STATE_CLOSE);

      if (mapLayouts.containsKey(id)) {
        final SwipeLayout layout = mapLayouts.get(id);
        layout.close(true);
      }
    }
  }

  private void closeOthers(String id, SwipeLayout swipeLayout) {
    synchronized (stateChangeLock) {
      // close other rows if openOnlyOne is true.
      if (getOpenCount() > 1) {
        for (Map.Entry<String, Integer> entry : mapStates.entrySet()) {
          if (!entry.getKey().equals(id)) {
            entry.setValue(SwipeLayout.STATE_CLOSE);
          }
        }

        for (SwipeLayout layout : mapLayouts.values()) {
          if (layout != swipeLayout) {
            layout.close(true);
          }
        }
      }
    }
  }

  private void setLockSwipe(boolean lock, String... id) {
    if (id == null || id.length == 0)
      return;

    if (lock)
      lockedSwipeSet.addAll(Arrays.asList(id));
    else
      lockedSwipeSet.removeAll(Arrays.asList(id));

    for (String s : id) {
      SwipeLayout layout = mapLayouts.get(s);
      if (layout != null) {
        layout.setLockDrag(lock);
      }
    }
  }

  private int getOpenCount() {
    int total = 0;

    for (int state : mapStates.values()) {
      if (state == SwipeLayout.STATE_OPEN || state == SwipeLayout.STATE_OPENING) {
        total++;
      }
    }

    return total;
  }
}