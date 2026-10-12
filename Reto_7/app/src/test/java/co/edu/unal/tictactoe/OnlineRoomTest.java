package co.edu.unal.tictactoe;

import org.junit.Test;

import static org.junit.Assert.*;

public class OnlineRoomTest {
    @Test public void onlyOtherPlayersCanJoinAnEmptyWaitingRoom() {
        OnlineRoom room = new OnlineRoom("room", "host", "", OnlineRoom.WAITING);
        assertFalse(room.canJoin("host"));
        assertFalse(room.canJoin(null));
        assertFalse(room.canJoin(""));
        assertTrue(room.canJoin("guest"));
        assertFalse(room.contains(""));
    }

    @Test public void fullAndClosedRoomsCannotBeJoined() {
        OnlineRoom room = new OnlineRoom("room", "host", "guest", OnlineRoom.READY);
        assertTrue(room.contains("host"));
        assertTrue(room.contains("guest"));
        assertFalse(room.contains("outsider"));
        assertFalse(room.canJoin("outsider"));
        assertFalse(new OnlineRoom("room", "host", "", OnlineRoom.CLOSED).canJoin("guest"));
    }
}
