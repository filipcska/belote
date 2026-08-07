package com.belote.ui;

import com.belote.model.Card;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * A playing card drawn from shapes and text, so the jar needs no image assets.
 */
public final class CardView extends StackPane {

    public static final double WIDTH = 64;
    public static final double HEIGHT = 92;

    private final Card card;
    private final Rectangle background;
    private boolean playable = true;

    public CardView(Card card, boolean faceUp) {
        this.card = card;

        background = new Rectangle(WIDTH, HEIGHT);
        background.setArcWidth(10);
        background.setArcHeight(10);
        background.setStroke(Color.rgb(40, 40, 40));
        background.setStrokeWidth(1.2);
        getChildren().add(background);

        if (faceUp) {
            background.setFill(Color.web("#fdfdf7"));
            Text label = new Text(card.rank().label() + "\n" + card.suit().symbol());
            label.setTextAlignment(TextAlignment.CENTER);
            label.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));
            label.setFill(card.suit().isBlack() ? Color.web("#1a1a1a") : Color.web("#c0392b"));
            getChildren().add(label);
        } else {
            background.setFill(Color.web("#2c5f8a"));
            Rectangle inner = new Rectangle(WIDTH - 12, HEIGHT - 12);
            inner.setArcWidth(6);
            inner.setArcHeight(6);
            inner.setFill(Color.web("#3d7fb5"));
            getChildren().add(inner);
        }

        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
    }

    public Card card() { return card; }

    /** Greys out and disables cards the rules forbid right now. */
    public void setPlayable(boolean value) {
        this.playable = value;
        setOpacity(value ? 1.0 : 0.42);
        setDisable(!value);
    }

    public boolean isPlayable() { return playable; }

    public void setHighlighted(boolean value) {
        background.setStroke(value ? Color.web("#f1c40f") : Color.rgb(40, 40, 40));
        background.setStrokeWidth(value ? 3 : 1.2);
    }
}
