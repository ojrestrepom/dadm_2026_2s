package co.edu.unal.tictactoe;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class LobbyActivityTest {
    @Test public void localGameRemainsAccessibleFromLobby() {
        try (ActivityScenario<LobbyActivity> scenario = ActivityScenario.launch(LobbyActivity.class)) {
            onView(withId(R.id.localGame)).check(matches(isEnabled())).perform(click());
            onView(withId(R.id.board)).check(matches(isDisplayed()));
            pressBack();
            onView(withText(R.string.lobby_title)).check(matches(isDisplayed()));
        }
    }

    @Test public void recreationKeepsLobbyAndLocalAccess() {
        try (ActivityScenario<LobbyActivity> scenario = ActivityScenario.launch(LobbyActivity.class)) {
            scenario.recreate();
            onView(withText(R.string.lobby_title)).check(matches(isDisplayed()));
            onView(withId(R.id.localGame)).check(matches(isEnabled()));
        }
    }
}
