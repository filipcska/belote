package com.belote.ui;

import com.belote.ai.BotPlayer;
import com.belote.game.Announcement;
import com.belote.game.GameEngine;
import com.belote.game.HandResult;
import com.belote.game.ScoreCalculator;
import com.belote.i18n.I18n;
import com.belote.model.Bid;
import com.belote.model.Card;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Seat;
import com.belote.model.Trick;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** JavaFX front end for the Belote engine. */
public final class BeloteApp extends Application {

    private static final Duration BOT_THINK = Duration.seconds(0.75);
    private static final Duration TRICK_HOLD = Duration.seconds(1.3);

    private final GameEngine engine = new GameEngine();
    private final Map<Seat, BotPlayer> bots = BotPlayer.createBots();

    /** Blocks bot scheduling while a trick or dialog is on screen. */
    private boolean busy;
    private Trick heldTrick;

    private BorderPane root;
    private BorderPane table;
    private VBox sidebar;
    private HBox toolbar;
    private VBox bidPane;
    private final VBox announcementLog = new VBox(3);
    private final Label statusLabel = new Label();

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #14532d;");

        toolbar = buildToolbar();
        sidebar = new VBox(10);
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(260);
        sidebar.setStyle("-fx-background-color: rgba(0,0,0,0.30);");

        table = new BorderPane();
        table.setPadding(new Insets(10));

        bidPane = new VBox(8);
        bidPane.setPadding(new Insets(10));
        bidPane.setAlignment(Pos.CENTER);
        bidPane.setStyle("-fx-background-color: rgba(0,0,0,0.30);");

        root.setTop(toolbar);
        root.setCenter(table);
        root.setRight(sidebar);
        root.setBottom(bidPane);

        engine.addListener(new GameEngine.Listener() {
            @Override public void onStateChanged() { refresh(); maybeAdvance(); }
            @Override public void onTrickComplete(Trick trick, Seat winner) { holdTrick(trick); }
            @Override public void onHandComplete(HandResult result) { showHandResult(result); }
            @Override public void onGameOver(int team) { /* handled after the result dialog */ }
            @Override public void onAnnouncement(Announcement a) { logAnnouncement(a); }
        });

        I18n.addListener(() -> { refresh(); stage.setTitle(I18n.t("app.title")); });

        Scene scene = new Scene(root, 1180, 800);
        stage.setScene(scene);
        stage.setTitle(I18n.t("app.title"));
        stage.show();

        engine.newGame();
    }

    // -------------------------------------------------------------- toolbar

    private HBox buildToolbar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: rgba(0,0,0,0.45);");

        Label title = new Label(I18n.t("app.title"));
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        Button newGame = new Button(I18n.t("app.newGame"));
        newGame.setOnAction(e -> {
            busy = false;
            heldTrick = null;
            announcementLog.getChildren().clear();
            engine.newGame();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label langLabel = new Label(I18n.t("app.language"));
        langLabel.setTextFill(Color.WHITE);

        ToggleGroup group = new ToggleGroup();
        ToggleButton en = new ToggleButton("EN");
        ToggleButton bg = new ToggleButton("БГ");
        en.setToggleGroup(group);
        bg.setToggleGroup(group);
        boolean english = I18n.locale().equals(I18n.ENGLISH);
        en.setSelected(english);
        bg.setSelected(!english);
        en.setOnAction(e -> { en.setSelected(true); I18n.setLocale(I18n.ENGLISH); });
        bg.setOnAction(e -> { bg.setSelected(true); I18n.setLocale(I18n.BULGARIAN); });

        bar.getChildren().addAll(title, newGame, spacer, langLabel, en, bg);
        return bar;
    }

    // --------------------------------------------------------------- render

    private void refresh() {
        if (root == null) return;
        root.setTop(buildToolbar());
        renderTable();
        renderSidebar();
        renderBidPane();
    }

    private void renderTable() {
        table.setTop(wrap(handRow(Seat.NORTH, false), Pos.CENTER));
        table.setLeft(wrap(handColumn(Seat.WEST), Pos.CENTER));
        table.setRight(wrap(handColumn(Seat.EAST), Pos.CENTER));
        table.setBottom(wrap(handRow(Seat.SOUTH, true), Pos.CENTER));
        table.setCenter(centreArea());
    }

    private static StackPane wrap(Region node, Pos pos) {
        StackPane pane = new StackPane(node);
        StackPane.setAlignment(node, pos);
        pane.setPadding(new Insets(6));
        return pane;
    }

    /** North and South sit horizontally; only South is dealt face up. */
    private Region handRow(Seat seat, boolean faceUp) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);

        HBox cards = new HBox(6);
        cards.setAlignment(Pos.CENTER);
        for (CardView view : buildHand(seat, faceUp)) {
            cards.getChildren().add(view);
        }

        box.getChildren().addAll(seatLabel(seat), cards);
        return box;
    }

    /** East and West are stacked vertically down the sides, always face down. */
    private Region handColumn(Seat seat) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);

        VBox cards = new VBox(-58);   // overlap so eight cards fit the height
        cards.setAlignment(Pos.CENTER);
        for (CardView view : buildHand(seat, false)) {
            cards.getChildren().add(view);
        }

        box.getChildren().addAll(seatLabel(seat), cards);
        return box;
    }

    private Label seatLabel(Seat seat) {
        String name = I18n.t(seat.key());
        if (seat.isHuman()) name += " (" + I18n.t("seat.you") + ")";
        if (seat == engine.activeSeat()) name = "▶ " + name;
        Label l = new Label(name + "  " + I18n.t("label.tricks", engine.tricksWon()[seat.team()]));
        l.setTextFill(seat == engine.activeSeat() ? Color.web("#f1c40f") : Color.WHITE);
        l.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        return l;
    }

    private List<CardView> buildHand(Seat seat, boolean faceUp) {
        List<Card> hand = new ArrayList<>(engine.player(seat).hand());
        ContractType type = engine.contractType();
        hand.sort(Comparator
                .comparingInt((Card c) -> c.suit().ordinal())
                .thenComparing(Comparator.comparingInt(
                        (Card c) -> c.strength(type != null && type.isTrumpSuit(c.suit()))).reversed()));

        List<Card> legal = seat.isHuman() ? engine.legalMoves(seat) : List.of();
        List<CardView> views = new ArrayList<>(hand.size());

        for (Card card : hand) {
            CardView view = new CardView(card, faceUp);
            if (seat.isHuman()) {
                boolean playable = engine.phase() == GameEngine.Phase.PLAYING
                        && !busy
                        && legal.contains(card);
                view.setPlayable(playable);
                if (playable) {
                    view.setOnMouseClicked(e -> engine.playCard(Seat.SOUTH, card));
                    view.setOnMouseEntered(e -> view.setHighlighted(true));
                    view.setOnMouseExited(e -> view.setHighlighted(false));
                }
            }
            views.add(view);
        }
        return views;
    }

    private VBox centreArea() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);

        Trick shown = heldTrick != null ? heldTrick : engine.currentTrick();
        FlowPane cards = new FlowPane(10, 10);
        cards.setAlignment(Pos.CENTER);
        cards.setPrefWrapLength(360);

        if (shown != null) {
            for (Trick.Play p : shown.plays()) {
                VBox cell = new VBox(4);
                cell.setAlignment(Pos.CENTER);
                Label who = new Label(I18n.t(p.seat().key()));
                who.setTextFill(Color.web("#d5f5e3"));
                cell.getChildren().addAll(who, new CardView(p.card(), true));
                cards.getChildren().add(cell);
            }
        }

        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        statusLabel.setText(statusText());

        box.getChildren().addAll(statusLabel, cards);
        return box;
    }

    private String statusText() {
        switch (engine.phase()) {
            case BIDDING: {
                Seat active = engine.activeSeat();
                String phase = I18n.t("phase.bidding");
                if (active == Seat.SOUTH) return phase + " — " + I18n.t("label.yourTurn");
                return phase + " — " + I18n.t("label.waiting", I18n.t(active.key()));
            }
            case PLAYING: {
                Seat active = engine.activeSeat();
                if (active == Seat.SOUTH) return I18n.t("label.yourTurn");
                return I18n.t("label.turn", I18n.t(active.key()));
            }
            case HAND_OVER:
                return I18n.t("phase.handOver");
            default:
                return I18n.t("phase.gameOver");
        }
    }

    // -------------------------------------------------------------- sidebar

    private void renderSidebar() {
        sidebar.getChildren().clear();

        sidebar.getChildren().add(header(I18n.t("score.title")));
        sidebar.getChildren().add(white(I18n.t("score.target", ScoreCalculator.WINNING_THRESHOLD)));
        sidebar.getChildren().add(white(I18n.t("score.team1") + ": " + engine.gameScore()[0]));
        sidebar.getChildren().add(white(I18n.t("score.team2") + ": " + engine.gameScore()[1]));
        if (engine.hangingPoints() > 0) {
            sidebar.getChildren().add(white(I18n.t("label.hanging", engine.hangingPoints())));
        }

        sidebar.getChildren().add(new Separator());
        sidebar.getChildren().add(white(I18n.t("label.dealer", I18n.t(engine.dealer().key()))));

        Contract c = engine.contract();
        if (c == null) {
            sidebar.getChildren().add(white(I18n.t("label.noContract")));
        } else {
            sidebar.getChildren().add(white(I18n.t("label.contract", I18n.t(c.type().key()))));
            sidebar.getChildren().add(white(I18n.t("label.declarer", I18n.t(c.declarer().key()))));
            if (c.doubleState() == Contract.DoubleState.DOUBLED) {
                sidebar.getChildren().add(white(I18n.t("label.doubled")));
            } else if (c.doubleState() == Contract.DoubleState.REDOUBLED) {
                sidebar.getChildren().add(white(I18n.t("label.redoubled")));
            }
            sidebar.getChildren().add(white(I18n.t("score.handPoints") + ": "
                    + engine.trickPoints()[0] + " / " + engine.trickPoints()[1]));
        }

        sidebar.getChildren().add(new Separator());
        sidebar.getChildren().add(header(I18n.t("result.announcements")));
        ScrollPane scroll = new ScrollPane(announcementLog);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sidebar.getChildren().add(scroll);
    }

    private void logAnnouncement(Announcement a) {
        Label l = white(I18n.t("ann.declared",
                I18n.t(a.seat().key()), I18n.t(a.type().key()), a.points()));
        l.setWrapText(true);
        announcementLog.getChildren().add(l);
    }

    private static Label header(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        l.setTextFill(Color.web("#f1c40f"));
        return l;
    }

    private static Label white(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.WHITE);
        l.setWrapText(true);
        return l;
    }

    // ------------------------------------------------------------- bidding

    private void renderBidPane() {
        bidPane.getChildren().clear();
        if (engine.phase() != GameEngine.Phase.BIDDING) return;
        if (engine.activeSeat() != Seat.SOUTH) return;

        FlowPane buttons = new FlowPane(8, 8);
        buttons.setAlignment(Pos.CENTER);
        for (Bid bid : engine.legalBids(Seat.SOUTH)) {
            Button b = new Button(bidLabel(bid));
            b.setOnAction(e -> engine.placeBid(Seat.SOUTH, bid));
            buttons.getChildren().add(b);
        }
        bidPane.getChildren().add(buttons);
    }

    private static String bidLabel(Bid bid) {
        if (bid.kind() == Bid.Kind.CONTRACT) {
            ContractType t = bid.contract();
            String name = I18n.t(t.key());
            return t.isSuitContract() ? name + " " + t.trumpSuit().symbol() : name;
        }
        return I18n.t(bid.key());
    }

    // ---------------------------------------------------------------- flow

    private void maybeAdvance() {
        if (busy) return;
        Seat active = engine.activeSeat();
        if (active == null || active.isHuman()) return;

        busy = true;
        PauseTransition pause = new PauseTransition(BOT_THINK);
        pause.setOnFinished(e -> {
            busy = false;
            BotPlayer bot = bots.get(active);
            if (engine.phase() == GameEngine.Phase.BIDDING) {
                engine.placeBid(active, bot.chooseBid(engine));
            } else if (engine.phase() == GameEngine.Phase.PLAYING) {
                engine.playCard(active, bot.chooseCard(engine));
            }
        });
        pause.play();
    }

    private void holdTrick(Trick trick) {
        heldTrick = trick;
        busy = true;
        PauseTransition pause = new PauseTransition(TRICK_HOLD);
        pause.setOnFinished(e -> {
            heldTrick = null;
            busy = false;
            refresh();
            maybeAdvance();
        });
        pause.play();
    }

    private void showHandResult(HandResult result) {
        busy = true;
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(I18n.t("result.title"));
            alert.setHeaderText(I18n.t(result.outcome().key()));
            alert.setContentText(resultText(result));
            alert.getButtonTypes().setAll(new javafx.scene.control.ButtonType(I18n.t("result.continue")));
            alert.showAndWait();

            busy = false;
            heldTrick = null;
            if (engine.phase() == GameEngine.Phase.GAME_OVER) {
                showGameOver();
            } else {
                announcementLog.getChildren().clear();
                engine.nextHand();
            }
        });
    }

    private String resultText(HandResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("result.rawTotal")).append(": ")
          .append(r.rawPoints()[0]).append(" / ").append(r.rawPoints()[1]).append('\n');
        sb.append(I18n.t("result.rounded")).append(": ")
          .append(r.roundedPoints()[0]).append(" / ").append(r.roundedPoints()[1]).append('\n');
        sb.append(I18n.t("result.awarded")).append(": ")
          .append(r.awarded()[0]).append(" / ").append(r.awarded()[1]).append('\n');
        if (r.multiplier() > 1) {
            sb.append(r.multiplier() == 2 ? I18n.t("label.doubled") : I18n.t("label.redoubled"))
              .append('\n');
        }
        if (r.isCapot()) {
            sb.append(I18n.t("result.capot")).append('\n').append(I18n.t("result.capotNoWin")).append('\n');
        }
        if (r.hangingCreated() > 0) {
            sb.append(I18n.t("label.hanging", r.hangingCreated())).append('\n');
        }
        return sb.toString();
    }

    private void showGameOver() {
        int team = engine.winningTeam();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("phase.gameOver"));
        alert.setHeaderText(I18n.t("game.winner",
                I18n.t(team == 0 ? "score.team1" : "score.team2")));
        alert.setContentText(engine.gameScore()[0] + " : " + engine.gameScore()[1]);
        alert.showAndWait();
    }
}
