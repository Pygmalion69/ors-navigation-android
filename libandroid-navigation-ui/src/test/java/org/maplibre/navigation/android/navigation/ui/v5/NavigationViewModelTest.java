package org.maplibre.navigation.android.navigation.ui.v5;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;

import org.maplibre.geojson.Point;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayer;
import org.maplibre.navigation.core.navigation.MapLibreNavigation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class NavigationViewModelTest {

  @Test
  public void stopNavigation_progressListenersAreRemoved() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    NavigationViewRouter router = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation, router);

    viewModel.stopNavigation();

    verify(navigation).removeProgressChangeListener(null);
  }

  @Test
  public void stopNavigation_milestoneListenersAreRemoved() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    NavigationViewRouter router = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation, router);

    viewModel.stopNavigation();

    verify(navigation).removeMilestoneEventListener(null);
  }

  @Test
  public void stopNavigation_mapOfflineManagerOnDestroyIsCalledIfNotNull() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    NavigationViewRouter router = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation, router);

    viewModel.onDestroy(false);
  }

  @Test
  public void updateRoute_navigationIsNotUpdatedWhenChangingConfigurations() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    DirectionsRoute route = mock(DirectionsRoute.class);
    NavigationViewRouter router = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation,router);
    viewModel.onDestroy(true);

    viewModel.updateRoute(route);

    verify(navigation, times(0)).startNavigation(route);
  }

  @Test
  public void navigationRouter_onDestroyInvokedWhenViewModelIsDestroyed() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    NavigationViewRouter mockedRouter = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation, mockedRouter);
    viewModel.onCleared();
    verify(mockedRouter).onDestroy();
  }

  @Test
  public void updateRoute_navigationIsUpdated() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    LocationEngineConductor conductor = mock(LocationEngineConductor.class);
    NavigationViewEventDispatcher dispatcher = mock(NavigationViewEventDispatcher.class);
    SpeechPlayer speechPlayer = mock(SpeechPlayer.class);
    DirectionsRoute route = mock(DirectionsRoute.class);
    NavigationViewModel viewModel = new NavigationViewModel(
      application, navigation, conductor, dispatcher, speechPlayer
    );
    viewModel.isOffRoute.postValue(true);

    viewModel.updateRoute(route);

    verify(navigation).startNavigation(route);
  }

  @Test
  public void isMuted_falseWithNullSpeechPlayer() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    LocationEngineConductor conductor = mock(LocationEngineConductor.class);
    NavigationViewEventDispatcher dispatcher = mock(NavigationViewEventDispatcher.class);
    SpeechPlayer speechPlayer = null;
    NavigationViewModel viewModel = new NavigationViewModel(
      application, navigation, conductor, dispatcher, speechPlayer
    );

    boolean isMuted = viewModel.isMuted();

    assertFalse(isMuted);
  }

  @Test
  public void isMuted_trueWithMutedSpeechPlayer() {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    LocationEngineConductor conductor = mock(LocationEngineConductor.class);
    NavigationViewEventDispatcher dispatcher = mock(NavigationViewEventDispatcher.class);
    SpeechPlayer speechPlayer = mock(SpeechPlayer.class);
    when(speechPlayer.isMuted()).thenReturn(true);
    NavigationViewModel viewModel = new NavigationViewModel(
      application, navigation, conductor, dispatcher,  speechPlayer
    );

    boolean isMuted = viewModel.isMuted();

    assertTrue(isMuted);
  }

  @Test
  public void handleOffRouteEvent_importedRouteClearsOffRouteState() throws Exception {
    Application application = mock(Application.class);
    MapLibreNavigation navigation = mock(MapLibreNavigation.class);
    NavigationViewRouter router = mock(NavigationViewRouter.class);
    NavigationViewModel viewModel = new NavigationViewModel(application, navigation, router);
    viewModel.isOffRoute.setValue(true);

    Field importedRouteNavigationField = NavigationViewModel.class.getDeclaredField("isImportedRouteNavigation");
    importedRouteNavigationField.setAccessible(true);
    importedRouteNavigationField.setBoolean(viewModel, true);

    Field offRouteMessageShownField = NavigationViewModel.class.getDeclaredField("importedRouteOffRouteMessageShown");
    offRouteMessageShownField.setAccessible(true);
    offRouteMessageShownField.setBoolean(viewModel, true);

    Method handleOffRouteEventMethod = NavigationViewModel.class.getDeclaredMethod("handleOffRouteEvent", Point.class);
    handleOffRouteEventMethod.setAccessible(true);
    handleOffRouteEventMethod.invoke(viewModel, Point.fromLngLat(8.68, 49.41));

    assertFalse(viewModel.isOffRoute());
  }
}
