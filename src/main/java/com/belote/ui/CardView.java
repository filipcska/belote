package com.belote.ui;

import com.belote.model.Card;
import com.belote.model.Rank;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A playing card. The face is drawn from vector text and shapes (corner indices
 * and pips), so it stays sharp at any size. Only the picture on J, Q and K and
 * the card back are images, from the GreyWyvern PNG deck in {@code /cards}
 * (public domain; see {@code LICENCE-greywyvern.txt}). A missing image falls
 * back to a drawn placeholder, so the game still runs.
 */
public final class CardView extends StackPane {

    public static final double WIDTH = 64;
    public static final double HEIGHT = 96;

    private static final Color RED = Color.web("#c0392b");
    private static final Color BLACK = Color.web("#1a1a1a");
    private static final Color OUTLINE = Color.rgb(40, 40, 40);

    private static final double[] LEFT_RIGHT_3 = {0, 0.5, 1};
    private static final double[] LEFT_RIGHT_4 = {0, 1.0 / 3, 2.0 / 3, 1};
    private static final double COLUMN_LEFT = 23;
    private static final double COLUMN_CENTRE = 32;
    private static final double COLUMN_RIGHT = 41;
    private static final double PIPS_TOP = 15;
    private static final double PIPS_HEIGHT = 66;

    private static final Map<String, Image> IMAGES = new HashMap<>();

    private final Card card;
    private final Rectangle background;
    private boolean playable = true;

    public CardView(Card card, boolean faceUp) {
        this.card = card;

        background = new Rectangle(WIDTH, HEIGHT);
        background.setArcWidth(10);
        background.setArcHeight(10);
        background.setStroke(OUTLINE);
        background.setStrokeWidth(1.2);
        getChildren().add(background);

        if (faceUp) {
            background.setFill(Color.web("#fdfdf7"));
            getChildren().add(face());
        } else {
            Image back = image("back");
            if (back != null) {
                background.setFill(Color.TRANSPARENT);
                getChildren().add(imageView(back, WIDTH, HEIGHT));
            } else {
                background.setFill(Color.web("#2c5f8a"));
                Rectangle inner = new Rectangle(WIDTH - 12, HEIGHT - 12);
                inner.setArcWidth(6);
                inner.setArcHeight(6);
                inner.setFill(Color.web("#3d7fb5"));
                getChildren().add(inner);
            }
        }

        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
    }

    private Pane face() {
        Color ink = card.suit().isBlack() ? BLACK : RED;
        String suit = card.suit().symbol();
        Pane face = new Pane();
        face.setMinSize(WIDTH, HEIGHT);
        face.setMaxSize(WIDTH, HEIGHT);

        Group corner = new Group(text(card.rank().label(), 13, ink, 9, 12),
                text(suit, 13, ink, 9, 25));
        Group opposite = new Group(text(card.rank().label(), 13, ink, 9, 12),
                text(suit, 13, ink, 9, 25));
        opposite.getTransforms().add(new Rotate(180, WIDTH / 2, HEIGHT / 2));
        face.getChildren().addAll(corner, opposite);

        Rank rank = card.rank();
        switch (rank) {
            case ACE -> face.getChildren().add(text(suit, 34, ink, COLUMN_CENTRE, HEIGHT / 2));
            case JACK, QUEEN, KING -> face.getChildren().add(court(ink));
            case SEVEN -> pips(face, suit, ink, LEFT_RIGHT_3, 0.25);
            case EIGHT -> pips(face, suit, ink, LEFT_RIGHT_3, 0.25, 0.75);
            case NINE -> pips(face, suit, ink, LEFT_RIGHT_4, 0.5);
            case TEN -> pips(face, suit, ink, LEFT_RIGHT_4, 1.0 / 6, 5.0 / 6);
        }
        return face;
    }

    /** Pips in the two side columns at the given rows, plus any in the middle column. */
    private static void pips(Pane face, String suit, Color ink, double[] sideRows, double... centreRows) {
        for (double row : sideRows) {
            face.getChildren().add(pip(suit, ink, COLUMN_LEFT, row));
            face.getChildren().add(pip(suit, ink, COLUMN_RIGHT, row));
        }
        for (double row : centreRows) {
            face.getChildren().add(pip(suit, ink, COLUMN_CENTRE, row));
        }
    }

    /** A pip; those in the lower half are upside down, as on a real card. */
    private static Text pip(String suit, Color ink, double x, double row) {
        Text pip = text(suit, 15, ink, x, PIPS_TOP + row * PIPS_HEIGHT);
        if (row > 0.5) pip.setRotate(180);
        return pip;
    }

    /** The picture on a court card, or the rank letter if the image is missing. */
    private Pane court(Color ink) {
        double w = 32, h = 56;
        Pane frame = new Pane();
        frame.setPrefSize(w, h);
        frame.setLayoutX(COLUMN_CENTRE - w / 2);
        frame.setLayoutY((HEIGHT - h) / 2);
        Image art = image("art_" + card.suit().name().toLowerCase() + "_" + card.rank().label());
        if (art != null) {
            frame.getChildren().add(imageView(art, w, h));
        } else {
            frame.getChildren().add(text(card.rank().label(), 30, ink, w / 2, h / 2));
        }
        Rectangle border = new Rectangle(w, h);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(OUTLINE);
        border.setStrokeWidth(1);
        frame.getChildren().add(border);
        return frame;
    }

    private static ImageView imageView(Image image, double w, double h) {
        ImageView view = new ImageView(image);
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.setSmooth(true);
        return view;
    }

    /** A bold text centred on (cx, cy). */
    private static Text text(String value, double size, Color ink, double cx, double cy) {
        Text t = new Text(value);
        t.setFont(Font.font("SansSerif", FontWeight.BOLD, size));
        t.setFill(ink);
        Bounds b = t.getLayoutBounds();
        t.setX(cx - b.getWidth() / 2);
        t.setY(cy - b.getMinY() - b.getHeight() / 2);
        return t;
    }

    /** Loads {@code /cards/<name>.png} once; null when the file is not bundled. */
    private static Image image(String name) {
        return IMAGES.computeIfAbsent(name, n -> {
            try (InputStream in = CardView.class.getResourceAsStream("/cards/" + n + ".png")) {
                return in == null ? null : new Image(in, WIDTH * 2, HEIGHT * 2, true, true);
            } catch (Exception e) {
                return null;
            }
        });
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
        background.setStroke(value ? Color.web("#f1c40f") : OUTLINE);
        background.setStrokeWidth(value ? 3 : 1.2);
    }
}
