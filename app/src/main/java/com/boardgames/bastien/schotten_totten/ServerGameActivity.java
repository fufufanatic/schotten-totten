package com.boardgames.bastien.schotten_totten;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.boardgames.bastien.schotten_totten.exceptions.NoPlayerException;
import com.boardgames.bastien.schotten_totten.model.Player;
import com.boardgames.bastien.schotten_totten.model.PlayerType;
import com.boardgames.bastien.schotten_totten.server.GameClient;
import com.boardgames.bastien.schotten_totten.server.GameClientInterface;
import com.boardgames.bastien.schotten_totten.server.GameDoNotExistException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class ServerGameActivity extends GameActivity {

    private final GameClientInterface client = new GameClient();
    private PlayerType type;
    private String gameName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getStringExtra("type").equals(PlayerType.ONE.toString())
                ? PlayerType.ONE : PlayerType.TWO;
        gameName = getIntent().getStringExtra("gameName");

        try {
            this.gameManager = client.getGame(gameName).get();
            initUI(this.gameManager.getGame().getPlayer(type).getHand());
            updateTextField();
            if (!this.gameManager.getGame().getPlayingPlayerType().equals(type)) {
                disableClick();
                Executors.newSingleThreadExecutor().submit(new GameClientThread());
            }
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof GameDoNotExistException) {
                showAlertMessage(getString(R.string.warning_title),
                        gameName + getString(R.string.game_do_not_exist),true, true);
            } else {
                showErrorMessage(e);
            }
        } catch (final Exception e) {
            showErrorMessage(e);
        }
    }

    @Override
    protected void endOfTurn() throws NoPlayerException {
        updateUI();
        disableClick();
        gameManager.swapPlayingPlayer();
        try {
            client.updateGame(gameName, gameManager);
            Executors.newSingleThreadExecutor().submit(new GameClientThread());
        } catch (final ExecutionException | InterruptedException e) {
            showErrorMessage(e);
        }
        updateTextField();
    }

    private class GameClientThread implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            while(!client.getGame(gameName).get().getGame().getPlayingPlayerType().equals(type)) {
                Thread.sleep(2500);
            }
            gameManager = client.getGame(gameName).get();
            enableClick();
            // update ui
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        updateUI();
                        // check victory
                        try {
                            endOfTheGame(gameManager.getGame().getWinner());
                        } catch (final NoPlayerException e) {
                            // nothing to do, just continue to play
                            Toast.makeText(ServerGameActivity.this,
                                getString(R.string.it_is_your_turn), Toast.LENGTH_LONG).show();
                        }
                    } catch (final NoPlayerException ex) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                showErrorMessage(ex);
                            }
                        });
                    }
                }
            });
            return true;
        }
    }

    @Override
    protected void endOfTheGame(final Player winner) throws NoPlayerException {
        super.endOfTheGame(winner);
        if (winner.getPlayerType().equals(type)) {
            gameManager.swapPlayingPlayer();
            try {
                client.updateGame(gameName, gameManager);
                Executors.newSingleThreadExecutor().submit(new GameClientThread());
            } catch (final ExecutionException | InterruptedException e) {
                showErrorMessage(e);
            }
        } else {
            try {
                client.deleteGame(gameName);
                Executors.newSingleThreadExecutor().submit(new GameClientThread());
            } catch (final ExecutionException | InterruptedException e) {
                showErrorMessage(e);
            }
        }
    }

    @Override
    protected void updateTextField() throws NoPlayerException {
        final PlayerType playingPlayerType = gameManager.getGame().getPlayingPlayerType();
        final String message = playingPlayerType.equals(type) ?
                gameManager.getGame().getPlayingPlayer().getName() + getString(R.string.it_is_your_turn_message) :
                getString(R.string.not_your_turn_message) ;
        ((TextView) findViewById(R.id.textView)).setText(message);
    }
}
