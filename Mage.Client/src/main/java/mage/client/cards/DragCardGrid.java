package mage.client.cards;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import mage.cards.MageCard;
import mage.cards.decks.DeckCardInfo;
import mage.cards.decks.DeckCardLayout;
import mage.client.MageFrame;
import mage.client.constants.Constants;
import mage.client.dialog.PreferencesDialog;
import mage.client.plugins.impl.Plugins;
import mage.client.util.CardViewCardTypeComparator;
import mage.client.util.CardViewColorComparator;
import mage.client.util.CardViewColorIdentityComparator;
import mage.client.util.CardViewCostComparator;
import mage.client.util.CardViewNameComparator;
import mage.client.util.CardViewRarityComparator;
import mage.client.util.Event;
import mage.client.util.GUISizeHelper;
import mage.client.util.Listener;
import mage.constants.CardType;
import mage.view.CardView;
import mage.view.CardsView;
import org.apache.log4j.Logger;
import org.mage.card.arcane.CardRenderer;

/**
 * Created by StravantUser on 2016-09-20.
 */
public class DragCardGrid extends JPanel implements DragCardSource, DragCardTarget {

    private final static Logger LOGGER = Logger.getLogger(DragCardGrid.class);
    private Constants.DeckEditorMode mode;

    @Override
    public Collection<CardView> dragCardList() {
        ArrayList<CardView> selectedCards = new ArrayList<>();
        for (CardView card : allCards) {
            if (card.isSelected()) {
                selectedCards.add(card);
            }
        }
        return selectedCards;
    }

    @Override
    public void dragCardBegin() {

    }

    @Override
    public void dragCardEnd(DragCardTarget target) {
        if (target == this) {
            // Already handled by dragged onto handler
        } else if (target == null) {
            // Don't remove the cards, no target
        } else {
            // Remove dragged cards
            for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
                for (ArrayList<CardView> stack : gridRow) {
                    for (int i = 0; i < stack.size(); ++i) {
                        CardView card = stack.get(i);
                        if (card.isSelected()) {
                            stack.set(i, null);
                            removeCardView(card);
                            eventSource.removeSpecificCard(card, "remove-specific-card");
                        }
                    }
                }
            }
            trimGrid();
            layoutGrid();
            cardScroll.revalidate();
            cardScroll.repaint();
        }
    }

    @Override
    public void dragCardEnter(MouseEvent e) {
        insertArrow.setVisible(true);
    }

    @Override
    public void dragCardMove(MouseEvent e) {
        e = SwingUtilities.convertMouseEvent(this, e, cardContent);
        showDropPosition(e.getX(), e.getY());
    }

    private void showDropPosition(int x, int y) {
        // Clamp to region
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }

        // Determine column
        int cardWidth = getCardWidth();
        int cardHeight = getCardHeight();
        int cardTopHeight = CardRenderer.getCardTopHeight(cardWidth);
        int dx = x % (cardWidth + GRID_PADDING);
        int col = x / (cardWidth + GRID_PADDING);
        int gridWidth = cardGrid.isEmpty() ? 0 : cardGrid.get(0).size();

        if (dx < GRID_PADDING && col < gridWidth) {
            // Which row to add to?
            int curY = COUNT_LABEL_HEIGHT;
            int rowIndex = 0;
            for (int i = 0; i < cardGrid.size(); ++i) {
                int maxStack = maxStackSize.get(i);
                int rowHeight = cardTopHeight * (maxStack - 1) + cardHeight;
                int rowBottom = curY + rowHeight + COUNT_LABEL_HEIGHT;

                // Break out if we're in that row
                if (y < rowBottom) {
                    // Set the row
                    rowIndex = i;
                    break;
                } else {
                    rowIndex = i + 1;
                    curY = rowBottom;
                }
            }

            // Insert between two columns
            insertArrow.setIcon(INSERT_COL_ICON);
            insertArrow.setSize(64, 64);
            insertArrow.setLocation((cardWidth + GRID_PADDING) * col + GRID_PADDING / 2 - 32, curY);
        } else {
            // Clamp to a new col one after the current last one
            col = Math.min(col, gridWidth);

            // Determine place in the col
            int curY = COUNT_LABEL_HEIGHT;
            int rowIndex = 0;
            int offsetIntoStack = 0;
            for (int i = 0; i < cardGrid.size(); ++i) {
                int maxStack = maxStackSize.get(i);
                int rowHeight = cardTopHeight * (maxStack - 1) + cardHeight;
                int rowBottom = curY + rowHeight + COUNT_LABEL_HEIGHT;

                // Break out if we're in that row
                if (y < rowBottom) {
                    // Set the row
                    rowIndex = i;
                    offsetIntoStack = y - curY;
                    break;
                } else {
                    rowIndex = i + 1;
                    offsetIntoStack = y - rowBottom;
                    curY = rowBottom;
                }
            }

            // Get the appropirate stack
            ArrayList<CardView> stack;
            if (rowIndex < cardGrid.size() && col < cardGrid.get(0).size()) {
                stack = cardGrid.get(rowIndex).get(col);
            } else {
                stack = new ArrayList<>();
            }

            // Figure out position in the stack based on the offsetIntoRow
            int stackInsertIndex = (offsetIntoStack + cardTopHeight / 2) / cardTopHeight;
            stackInsertIndex = Math.max(0, Math.min(stackInsertIndex, stack.size()));

            // Position arrow
            insertArrow.setIcon(INSERT_ROW_ICON);
            insertArrow.setSize(64, 32);
            insertArrow.setLocation((cardWidth + GRID_PADDING) * col + GRID_PADDING + cardWidth / 2 - 32, curY + stackInsertIndex * cardTopHeight - 32);
        }
    }

    @Override
    public void dragCardExit(MouseEvent e) {
        insertArrow.setVisible(false);
    }

    @Override
    public void dragCardDrop(MouseEvent e, DragCardSource source, Collection<CardView> cards) {
        e = SwingUtilities.convertMouseEvent(this, e, cardContent);
        int x = e.getX();
        int y = e.getY();

        // Clamp to region
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }

        // If we're dragging onto ourself, erase the old cards (just null them out, we will
        // compact the grid removing the null gaps / empty rows & cols later)
        if (source == this) {
            for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
                for (ArrayList<CardView> stack : gridRow) {
                    for (int i = 0; i < stack.size(); ++i) {
                        if (cards.contains(stack.get(i))) {
                            stack.set(i, null);
                        }
                    }
                }
            }
        }

        // Determine column
        int cardWidth = getCardWidth();
        int cardHeight = getCardHeight();
        int cardTopHeight = CardRenderer.getCardTopHeight(cardWidth);
        int dx = x % (cardWidth + GRID_PADDING);
        int col = x / (cardWidth + GRID_PADDING);
        int gridWidth = cardGrid.isEmpty() ? 0 : cardGrid.get(0).size();

        if (dx < GRID_PADDING && col < gridWidth) {
            // Which row to add to?
            int curY = COUNT_LABEL_HEIGHT;
            int rowIndex = 0;
            for (int i = 0; i < cardGrid.size(); ++i) {
                int maxStack = maxStackSize.get(i);
                int rowHeight = cardTopHeight * (maxStack - 1) + cardHeight;
                int rowBottom = curY + rowHeight + COUNT_LABEL_HEIGHT;

                // Break out if we're in that row
                if (y < rowBottom) {
                    // Set the row
                    rowIndex = i;
                    break;
                } else {
                    rowIndex = i + 1;
                    curY = rowBottom;
                }
            }

            // Add a new row if needed
            if (rowIndex >= cardGrid.size()) {
                ArrayList<ArrayList<CardView>> newRow = new ArrayList<>();
                if (!cardGrid.isEmpty()) {
                    for (int colIndex = 0; colIndex < cardGrid.get(0).size(); ++colIndex) {
                        newRow.add(new ArrayList<>());
                    }
                }
                cardGrid.add(newRow);
                maxStackSize.add(0);
            }

            // Insert the new column to add to
            for (int i = 0; i < cardGrid.size(); ++i) {
                cardGrid.get(i).add(col, new ArrayList<>());
            }

            // Add the cards
            cardGrid.get(rowIndex).get(col).addAll(cards);
        } else {
            // Clamp to a new col one after the current last one
            col = Math.min(col, gridWidth);

            // Determine place in the col
            int curY = COUNT_LABEL_HEIGHT;
            int rowIndex = 0;
            int offsetIntoStack = 0;
            for (int i = 0; i < cardGrid.size(); ++i) {
                int maxStack = maxStackSize.get(i);
                int rowHeight = cardTopHeight * (maxStack - 1) + cardHeight;
                int rowBottom = curY + rowHeight + COUNT_LABEL_HEIGHT;

                // Break out if we're in that row
                if (y < rowBottom) {
                    // Set the row
                    rowIndex = i;
                    offsetIntoStack = y - curY;
                    break;
                } else {
                    rowIndex = i + 1;
                    offsetIntoStack = y - rowBottom;
                    curY = rowBottom;
                }
            }

            // Add a new row if needed
            if (rowIndex >= cardGrid.size()) {
                ArrayList<ArrayList<CardView>> newRow = new ArrayList<>();
                if (!cardGrid.isEmpty()) {
                    for (int colIndex = 0; colIndex < cardGrid.get(0).size(); ++colIndex) {
                        newRow.add(new ArrayList<>());
                    }
                }
                cardGrid.add(newRow);
                maxStackSize.add(0);
            }

            // Add a new col if needed
            if (col >= cardGrid.get(0).size()) {
                for (int i = 0; i < cardGrid.size(); ++i) {
                    cardGrid.get(i).add(new ArrayList<>());
                }
            }

            // Get the appropirate stack
            ArrayList<CardView> stack = cardGrid.get(rowIndex).get(col);

            // Figure out position in the stack based on the offsetIntoRow
            int stackInsertIndex = (offsetIntoStack + cardTopHeight / 2) / cardTopHeight;
            stackInsertIndex = Math.max(0, Math.min(stackInsertIndex, stack.size()));

            // Insert the cards
            stack.addAll(stackInsertIndex, cards);
        }

        if (source == this) {
            // Remove empty rows / cols / spaces in stacks
            trimGrid();
            layoutGrid();
            cardScroll.revalidate();
            cardScroll.repaint();
        } else {
            // Add new cards to grid
            for (CardView card : cards) {
                card.setSelected(true);
                addCardView(card, false);
                eventSource.addSpecificCard(card, "add-specific-card");
            }
            layoutGrid();
            cardContent.repaint();
        }
    }

    public void changeGUISize() {
        layoutGrid();
        cardScroll.getVerticalScrollBar().setUnitIncrement(CardRenderer.getCardTopHeight(getCardWidth()));
        cardContent.repaint();
    }

    public void cleanUp() {
        // Remove all of the cards from us
        for (MageCard cardView : cardViews.values()) {
            cardContent.remove(cardView);
        }

        // Clear out our tracking of stuff
        cardGrid.clear();
        maxStackSize.clear();
        allCards.clear();
        lastBigCard = null;
        clearCardEventListeners();
    }

    public void addCardEventListener(Listener<Event> listener) {
        eventSource.addListener(listener);
    }

    public void clearCardEventListeners() {
        eventSource.clearListeners();
    }

    public void setRole(Role role) {
        this.role = role;
        if (role == Role.SIDEBOARD) {
            creatureCountLabel.setVisible(false);
            landCountLabel.setVisible(false);
            cardSizeSliderLabel.setVisible(false);
        } else {
            creatureCountLabel.setVisible(true);
            landCountLabel.setVisible(true);
            cardSizeSliderLabel.setVisible(true);
        }
        updateCounts();
    }

    public void removeSelection() {
        for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
            for (ArrayList<CardView> stack : gridRow) {
                for (int i = 0; i < stack.size(); ++i) {
                    CardView card = stack.get(i);
                    if (card.isSelected()) {
                        eventSource.removeSpecificCard(card, "remove-specific-card");
                        stack.set(i, null);
                        removeCardView(card);
                    }
                }
            }
        }
        trimGrid();
        layoutGrid();
        cardContent.repaint();
    }

    public DeckCardLayout getCardLayout() {
        // 2D Array to put entries into
        List<List<List<DeckCardInfo>>> info = new ArrayList<>();
        for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
            List<List<DeckCardInfo>> row = new ArrayList<>();
            info.add(row);
            for (ArrayList<CardView> stack : gridRow) {
                row.add(stack.stream()
                        .map(card -> new DeckCardInfo(card.getName(), card.getCardNumber(), card.getExpansionSetCode()))
                        .collect(Collectors.toList()));
            }
        }

        // Store layout and settings then return them
        return new DeckCardLayout(info, saveSettings().toString());
    }

    public void setDeckEditorMode(Constants.DeckEditorMode mode) {
        this.mode = mode;
    }

    public enum Sort {
        NONE("No Sort", new Comparator<CardView>() {
            @Override
            public int compare(CardView o1, CardView o2) {
                // Always equal, sort into the first row
                return 0;
            }
        }),
        CARD_TYPE("Card Type", new CardViewCardTypeComparator()),
        CMC("Converted Mana Cost", new CardViewCostComparator()),
        COLOR("Color", new CardViewColorComparator()),
        COLOR_IDENTITY("Color Identity", new CardViewColorIdentityComparator()),
        RARITY("Rarity", new CardViewRarityComparator());

        Sort(String text, Comparator<CardView> comparator) {
            this.comparator = comparator;
            this.text = text;
        }

        public Comparator<CardView> getComparator() {
            return comparator;
        }

        public String getText() {
            return text;
        }

        private final Comparator<CardView> comparator;
        private final String text;
    }

    private abstract class CardTypeCounter {

        protected abstract boolean is(CardView card);

        int get() {
            return count;
        }

        void add(CardView card) {
            if (is(card)) {
                ++count;
            }
        }

        void remove(CardView card) {
            if (is(card)) {
                --count;
            }
        }
        private int count = 0;
    }

    // Counters we use
    private CardTypeCounter creatureCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.CREATURE);
        }
    };
    private CardTypeCounter landCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.LAND);
        }
    };

    private CardTypeCounter artifactCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.ARTIFACT);
        }
    };
    private CardTypeCounter enchantmentCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.ENCHANTMENT);
        }
    };
    private CardTypeCounter instantCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.INSTANT);
        }
    };
    private CardTypeCounter sorceryCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.SORCERY);
        }
    };
    private CardTypeCounter planeswalkerCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.PLANESWALKER);
        }
    };
    private final CardTypeCounter tribalCounter = new CardTypeCounter() {
        @Override
        protected boolean is(CardView card) {
            return card.getCardTypes().contains(CardType.TRIBAL);
        }
    };

    private final CardTypeCounter[] allCounters = {
        creatureCounter,
        landCounter,
        artifactCounter,
        enchantmentCounter,
        instantCounter,
        sorceryCounter,
        planeswalkerCounter,
        sorceryCounter,
        tribalCounter
    };

    // Listener
    public interface DragCardGridListener {

        void cardsSelected();

        void hideCards(Collection<CardView> card);

        void duplicateCards(Collection<CardView> cards);

        void showAll();
    };

    // Constants
    public static int COUNT_LABEL_HEIGHT = 20;
    public static int GRID_PADDING = 10;

    private final static ImageIcon INSERT_ROW_ICON = new ImageIcon(DragCardGrid.class.getClassLoader().getResource("editor_insert_row.png"));
    private final static ImageIcon INSERT_COL_ICON = new ImageIcon(DragCardGrid.class.getClassLoader().getResource("editor_insert_col.png"));

    // All of the current card views
    private final Map<UUID, MageCard> cardViews = new LinkedHashMap<>();
    private final ArrayList<CardView> allCards = new ArrayList<>();

    // Card listeners
    private final CardEventSource eventSource = new CardEventSource();

    // Last big card
    BigCard lastBigCard = null;

    // Top bar with dropdowns for sort / filter / etc
    JButton sortButton;
    JButton filterButton;
    JButton visibilityButton;
    JButton selectByButton;
    JButton analyseButton;

    // Popup for toolbar
    JPopupMenu filterPopup;
    JPopupMenu selectByTypePopup;

    JPopupMenu sortPopup;
    JPopupMenu selectByPopup;
    JCheckBox separateCreaturesCb;
    JTextField searchByTextField;

    JSlider cardSizeSlider;
    JLabel cardSizeSliderLabel;

    Map<Sort, AbstractButton> sortButtons = new HashMap<>();
    HashMap<CardType, AbstractButton> selectByTypeButtons = new HashMap<>();

    JLabel deckNameAndCountLabel;
    JLabel landCountLabel;
    JLabel creatureCountLabel;

    // Main two controls holding the scrollable card grid
    JScrollPane cardScroll;
    JLayeredPane cardContent;

    // Drag onto insert arrow
    JLabel insertArrow;

    // Card area selection panel
    SelectionBox selectionPanel;
    Set<CardView> selectionDragStartCards;
    int selectionDragStartX;
    int selectionDragStartY;

    // Card size mod
    float cardSizeMod = 1.0f;

    // The role (maindeck or sideboard)
    Role role = Role.MAINDECK;

    // Dragging
    private final CardDraggerGlassPane dragger = new CardDraggerGlassPane(this);

    // The grid of cards
    // The outermost array contains multiple rows of stacks of cards
    // The next inner array represents a row of stacks of cards
    // The innermost array represents a single vertical stack of cards
    private ArrayList<ArrayList<ArrayList<CardView>>> cardGrid;
    private ArrayList<Integer> maxStackSize = new ArrayList<>();
    private final ArrayList<ArrayList<JLabel>> stackCountLabels = new ArrayList<>();
    private Sort cardSort = Sort.CMC;
    private final ArrayList<CardType> selectByTypeSelected = new ArrayList<>();
    private boolean separateCreatures = true;

    public enum Role {
        MAINDECK("Maindeck"),
        SIDEBOARD("Sideboard");

        Role(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        private String name;
    }

    public static class Settings {

        public Sort sort;
        public boolean separateCreatures;
        public int cardSize;

        private final static Pattern parser = Pattern.compile("\\(([^,]*),([^,]*),([^)]*)\\)");

        public static Settings parse(String str) {
            Matcher m = parser.matcher(str);
            if (m.find()) {
                Settings s = new Settings();
                if (m.groupCount() > 0) {
                    s.sort = Sort.valueOf(m.group(1));
                }
                if (m.groupCount() > 1) {
                    s.separateCreatures = Boolean.valueOf(m.group(2));
                }
                if (m.groupCount() > 2) {
                    s.cardSize = Integer.valueOf(m.group(3));
                } else {
                    s.cardSize = 50;
                }
                return s;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return "(" + sort.toString() + "," + Boolean.toString(separateCreatures) + "," + Integer.toString(cardSize) + ")";
        }
    }

    public Settings saveSettings() {
        Settings s = new Settings();
        s.sort = cardSort;
        s.separateCreatures = separateCreatures;
        s.cardSize = cardSizeSlider.getValue();
        return s;
    }

    public void loadSettings(Settings s) {
        if (s != null) {
            setSort(s.sort);
            setSeparateCreatures(s.separateCreatures);
            setCardSize(s.cardSize);
            resort();
        }
    }

    public void setSeparateCreatures(boolean state) {
        separateCreatures = state;
        separateCreaturesCb.setSelected(state);
    }

    public void setSort(Sort s) {
        cardSort = s;
        sortButtons.get(s).setSelected(true);
    }

    public void setCardSize(int size) {
        cardSizeSlider.setValue(size);
    }

    // Constructor
    public DragCardGrid() {
        // Make sure that the card grid is populated with at least one (empty) stack to begin with
        cardGrid = new ArrayList<>();

        // Component init
        setLayout(new BorderLayout());
        setOpaque(false);

        // Editting mode
        this.mode = Constants.DeckEditorMode.LIMITED_BUILDING;

        // Toolbar
        sortButton = new JButton("Sort");
        filterButton = new JButton("Filter");
        visibilityButton = new JButton("Visibility");
        selectByButton = new JButton("Select By");
        analyseButton = new JButton("Mana");

        // Name and count label
        deckNameAndCountLabel = new JLabel();

        // Count labels
        landCountLabel = new JLabel("", new ImageIcon(getClass().getResource("/buttons/type_land.png")), SwingConstants.LEFT);
        landCountLabel.setToolTipText("Number of lands in deck");
        creatureCountLabel = new JLabel("", new ImageIcon(getClass().getResource("/buttons/type_creatures.png")), SwingConstants.LEFT);
        creatureCountLabel.setToolTipText("Number of creatures in deck");

        JPanel toolbar = new JPanel(new BorderLayout());
        JPanel toolbarInner = new JPanel();
        toolbar.setBackground(new Color(250, 250, 250, 150));
        toolbar.setOpaque(true);
        toolbarInner.setOpaque(false);
        toolbarInner.add(deckNameAndCountLabel);
        toolbarInner.add(landCountLabel);
        toolbarInner.add(creatureCountLabel);
        toolbarInner.add(sortButton);
        toolbarInner.add(filterButton);
        toolbarInner.add(visibilityButton);
        toolbarInner.add(selectByButton);
        toolbarInner.add(analyseButton);
        toolbar.add(toolbarInner, BorderLayout.WEST);
        JPanel sliderPanel = new JPanel(new GridBagLayout());
        sliderPanel.setOpaque(false);
        cardSizeSlider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 50);
        cardSizeSlider.setOpaque(false);
        cardSizeSlider.setPreferredSize(new Dimension(100, (int) cardSizeSlider.getPreferredSize().getHeight()));
        cardSizeSlider.addChangeListener(e -> {
            if (!cardSizeSlider.getValueIsAdjusting()) {
                // Fraction in [-1, 1]
                float sliderFrac = ((float) (cardSizeSlider.getValue() - 50)) / 50;
                // Convert to frac in [0.5, 2.0] exponentially
                cardSizeMod = (float) Math.pow(2, sliderFrac);
                // Update grid
                layoutGrid();
                cardContent.repaint();
            }
        });
        cardSizeSliderLabel = new JLabel("Card Size:");
        sliderPanel.add(cardSizeSliderLabel);
        sliderPanel.add(cardSizeSlider);
        toolbar.add(sliderPanel, BorderLayout.EAST);
        this.add(toolbar, BorderLayout.NORTH);

        // Content
        cardContent = new JLayeredPane();
        cardContent.setLayout(null);
        cardContent.setOpaque(false);
        cardContent.addMouseListener(new MouseAdapter() {
            private boolean isDragging = false;

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDragging = true;
                    beginSelectionDrag(e.getX(), e.getY(), e.isShiftDown());
                    updateSelectionDrag(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging) {
                    isDragging = false;
                    updateSelectionDrag(e.getX(), e.getY());
                    endSelectionDrag(e.getX(), e.getY());
                }
            }
        });
        cardContent.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateSelectionDrag(e.getX(), e.getY());
            }
        });
        cardScroll = new JScrollPane(cardContent,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        cardScroll.setOpaque(false);
        cardScroll.getViewport().setOpaque(false);
        cardScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        cardScroll.setBorder(BorderFactory.createLineBorder(Color.gray, 1));
        cardScroll.getVerticalScrollBar().setUnitIncrement(CardRenderer.getCardTopHeight(getCardWidth()));
        this.add(cardScroll, BorderLayout.CENTER);

        // Insert arrow
        insertArrow = new JLabel();
        insertArrow.setSize(20, 20);
        insertArrow.setVisible(false);
        cardContent.add(insertArrow, new Integer(1000));

        // Selection panel
        selectionPanel = new SelectionBox();
        selectionPanel.setVisible(false);
        cardContent.add(selectionPanel, new Integer(1001));

        // Load separate creatures setting
        separateCreatures = PreferencesDialog.getCachedValue(PreferencesDialog.KEY_DECK_EDITOR_LAST_SEPARATE_CREATURES, "false").equals("true");
        try {
            cardSort = Sort.valueOf(PreferencesDialog.getCachedValue(PreferencesDialog.KEY_DECK_EDITOR_LAST_SORT, Sort.NONE.toString()));
        } catch (IllegalArgumentException ex) {
            cardSort = Sort.NONE;
        }
        // Sort popup
        {
            sortPopup = new JPopupMenu();
            sortPopup.setLayout(new GridBagLayout());

            JPanel sortMode = new JPanel();
            sortMode.setLayout(new GridLayout(Sort.values().length, 1, 0, 2));
            sortMode.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Sort by..."));
            GridBagConstraints sortModeC = new GridBagConstraints();
            sortModeC.gridx = 0;
            sortModeC.gridy = 0;
            sortModeC.gridwidth = 1;
            sortModeC.gridheight = 1;
            sortModeC.fill = GridBagConstraints.HORIZONTAL;
            sortPopup.add(sortMode, sortModeC);

            ButtonGroup sortModeGroup = new ButtonGroup();
            for (final Sort s : Sort.values()) {
                JToggleButton button = new JToggleButton(s.getText());
                if (s == cardSort) {
                    button.setSelected(true);
                }
                sortButtons.put(s, button);
                sortMode.add(button);
                sortModeGroup.add(button);
                button.addActionListener(e -> {
                    cardSort = s;
                    PreferencesDialog.saveValue(PreferencesDialog.KEY_DECK_EDITOR_LAST_SORT, s.toString());
                    resort();
                });
            }

            JPanel sortOptions = new JPanel();
            sortOptions.setLayout(new BoxLayout(sortOptions, BoxLayout.Y_AXIS));
            sortOptions.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Sort options"));
            GridBagConstraints sortOptionsC = new GridBagConstraints();
            sortOptionsC.gridx = 0;
            sortOptionsC.gridy = 1;
            sortOptionsC.gridwidth = 1;
            sortOptionsC.gridheight = 1;
            sortPopup.add(sortOptions, sortOptionsC);

            separateCreaturesCb = new JCheckBox();
            separateCreaturesCb.setText("Creatures in separate row");
            separateCreaturesCb.setSelected(separateCreatures);
            separateCreaturesCb.addItemListener(e -> {
                setSeparateCreatures(separateCreaturesCb.isSelected());
                PreferencesDialog.saveValue(PreferencesDialog.KEY_DECK_EDITOR_LAST_SEPARATE_CREATURES, Boolean.toString(separateCreatures));
                resort();
            });
            sortOptions.add(separateCreaturesCb);
            sortPopup.pack();

            makeButtonPopup(sortButton, sortPopup);
        }

        // Visibility popup
        {
            final JPopupMenu visPopup = new JPopupMenu();
            JMenuItem hideSelected = new JMenuItem("Hide selected");
            hideSelected.addActionListener(e -> hideSelection());
            visPopup.add(hideSelected);
            JMenuItem showAll = new JMenuItem("Show all");
            showAll.addActionListener(e -> showAll());
            visPopup.add(showAll);
            visibilityButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    visPopup.show(e.getComponent(), 0, e.getComponent().getHeight());
                }
            });
        }

        // selectBy.. popup
        {
            selectByPopup = new JPopupMenu();
            selectByPopup.setLayout(new GridBagLayout());

            JPanel selectByTypeMode = new JPanel();
            selectByTypeMode.setLayout(new GridLayout(CardType.values().length, 1, 0, 2));
            selectByTypeMode.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Select by Type"));
            GridBagConstraints selectByTypeModeC = new GridBagConstraints();
            selectByTypeModeC.gridx = 0;
            selectByTypeModeC.gridy = 0;
            selectByTypeModeC.gridwidth = 1;
            selectByTypeModeC.gridheight = 1;
            selectByTypeModeC.fill = GridBagConstraints.HORIZONTAL;
            selectByPopup.add(selectByTypeMode, selectByTypeModeC);

            ButtonGroup selectByTypeModeGroup = new ButtonGroup();
            for (final CardType cardType : CardType.values()) {
                JToggleButton button = new JToggleButton(cardType.toString());

                selectByTypeButtons.put(cardType, button);
                selectByTypeMode.add(button);
                selectByTypeModeGroup.add(button);
                button.addActionListener(e -> {
                    //selectByTypeSelected.add(cardType);
                    button.setSelected(!button.isSelected());
                    reselectBy();
                });
            }

            JPanel selectBySearchPanel = new JPanel();
            selectBySearchPanel.setPreferredSize(new Dimension(150, 60));
            selectBySearchPanel.setLayout(new GridLayout(1, 1));
            selectBySearchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Search:"));
            GridBagConstraints selectBySearchPanelC = new GridBagConstraints();
            selectBySearchPanelC.gridx = 0;
            selectBySearchPanelC.gridy = 1;
            selectBySearchPanelC.gridwidth = 1;
            selectBySearchPanelC.gridheight = 1;
            selectBySearchPanelC.fill = GridBagConstraints.HORIZONTAL;
            selectBySearchPanelC.fill = GridBagConstraints.VERTICAL;

            searchByTextField = new JTextField();
            searchByTextField.setToolTipText("Searches for card names, types, rarity, casting cost and rules text.  NB: Mana symbols are written like {W},{U},{C} etc");
            searchByTextField.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    reselectBy();
                }

                public void keyTyped(KeyEvent e) {
                }

                public void keyPressed(KeyEvent e) {
                }
            });

            selectBySearchPanel.add(searchByTextField);
            selectByPopup.add(selectBySearchPanel, selectBySearchPanelC);
            makeButtonPopup(selectByButton, selectByPopup);
        }

        // Analyse Mana (aka #blue pips, #islands, #white pips, #plains etc.)
        analyseButton.setToolTipText("Counts coloured/colourless mana costs. Counts land types.");

        analyseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                analyseDeck();
            }
        });

        // Filter popup
        filterPopup = new JPopupMenu();
        filterPopup.setPreferredSize(new Dimension(300, 300));
        makeButtonPopup(filterButton, filterPopup);
        filterButton.setVisible(false);

        // Right click in card area
        initCardAreaPopup();

        // Update counts
        updateCounts();
    }

    public void initCardAreaPopup() {
        final JPopupMenu menu = new JPopupMenu();

        final JMenuItem hideSelected = new JMenuItem("Hide selected");
        hideSelected.addActionListener(e -> hideSelection());
        menu.add(hideSelected);

        JMenuItem showAll = new JMenuItem("Show all");
        showAll.addActionListener(e -> showAll());
        menu.add(showAll);

        JMenu sortMenu = new JMenu("Sort by...");
        final Map<Sort, JMenuItem> sortMenuItems = new LinkedHashMap<>();
        for (final Sort sort : Sort.values()) {
            JMenuItem subSort = new JCheckBoxMenuItem(sort.getText());
            sortMenuItems.put(sort, subSort);
            subSort.addActionListener(e -> {
                cardSort = sort;
                resort();
            });
            sortMenu.add(subSort);
        }
        sortMenu.add(new JPopupMenu.Separator());
        final JCheckBoxMenuItem separateButton = new JCheckBoxMenuItem("Separate creatures");
        separateButton.addActionListener(e -> {
            setSeparateCreatures(!separateCreatures);
            resort();
        });
        sortMenu.add(separateButton);
        menu.add(sortMenu);

        // Hook up to card content
        cardContent.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    for (Sort s : sortMenuItems.keySet()) {
                        sortMenuItems.get(s).setSelected(cardSort == s);
                    }
                    hideSelected.setEnabled(dragCardList().size() > 0);
                    separateButton.setSelected(separateCreatures);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Deselect all cards in this DragCardGrid
     */
    public void deselectAll() {
        for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
            for (ArrayList<CardView> stack : gridRow) {
                for (CardView card : stack) {
                    if (card.isSelected()) {
                        card.setSelected(false);
                        cardViews.get(card.getId()).update(card);
                    }
                }
            }
        }
    }

    private void hideSelection() {
        Collection<CardView> toHide = dragCardList();
        for (DragCardGridListener l : listeners) {
            l.hideCards(toHide);
        }
    }

    private void duplicateSelection() {
        Collection<CardView> toDuplicate = dragCardList();
        for (DragCardGridListener l : listeners) {
            l.duplicateCards(toDuplicate);
        }
    }

    private void showAll() {
        for (DragCardGridListener l : listeners) {
            l.showAll();
        }
    }

    /**
     * Selection drag handling
     */
    private void beginSelectionDrag(int x, int y, boolean shiftHeld) {
        // Show the selection panel
        selectionPanel.setVisible(true);
        selectionPanel.setLocation(x, y);
        cardScroll.revalidate();

        // Store the drag start location
        selectionDragStartX = x;
        selectionDragStartY = y;

        // Store the starting cards to include in the selection
        selectionDragStartCards = new HashSet<>();
        if (shiftHeld) {
            selectionDragStartCards.addAll(dragCardList());
        }

        // Notify selection
        notifyCardsSelected();
    }

    private void updateSelectionDrag(int x, int y) {
        // Coords
        int cardWidth = getCardWidth();
        int cardHeight = getCardHeight();
        int cardTopHeight = CardRenderer.getCardTopHeight(cardWidth);
        int x1 = Math.min(x, selectionDragStartX);
        int x2 = Math.max(x, selectionDragStartX);
        int y1 = Math.min(y, selectionDragStartY);
        int y2 = Math.max(y, selectionDragStartY);

        // Update selection panel size
        selectionPanel.setLocation(x1, y1);
        selectionPanel.setSize(x2 - x1, y2 - y1);

        // First and last cols
        int col1 = x1 / (cardWidth + GRID_PADDING);
        int col2 = x2 / (cardWidth + GRID_PADDING);
        int offsetIntoCol2 = x2 % (cardWidth + GRID_PADDING);
        if (offsetIntoCol2 < GRID_PADDING) {
            --col2;
        }

        int curY = COUNT_LABEL_HEIGHT;
        for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
            int stackStartIndex;
            if (y1 < curY) {
                stackStartIndex = 0;
            } else {
                stackStartIndex = (y1 - curY) / cardTopHeight;
            }
            int stackEndIndex;
            if (y2 < curY) {
                stackEndIndex = -1;
            } else {
                stackEndIndex = (y2 - curY) / cardTopHeight;
            }
            ArrayList<ArrayList<CardView>> gridRow = cardGrid.get(rowIndex);
            for (int col = 0; col < gridRow.size(); ++col) {
                ArrayList<CardView> stack = gridRow.get(col);
                int stackBottomBegin = curY + cardTopHeight * (stack.size());
                int stackBottomEnd = curY + cardTopHeight * (stack.size() - 1) + cardHeight;
                for (int i = 0; i < stack.size(); ++i) {
                    CardView card = stack.get(i);
                    MageCard view = cardViews.get(card.getId());
                    boolean inBoundsX = (col >= col1 && col <= col2);
                    boolean inBoundsY = (i >= stackStartIndex && i <= stackEndIndex);
                    boolean lastCard = (i == stack.size() - 1);
                    boolean inSeletionDrag = inBoundsX && (inBoundsY || (lastCard && (y2 >= stackBottomBegin && y1 <= stackBottomEnd)));
                    if (inSeletionDrag || selectionDragStartCards.contains(card)) {
                        if (!card.isSelected()) {
                            card.setSelected(true);
                            view.update(card);
                        }
                    } else if (card.isSelected()) {
                        card.setSelected(false);
                        view.update(card);
                    }
                }
            }
            curY += cardTopHeight * (maxStackSize.get(rowIndex) - 1) + cardHeight + COUNT_LABEL_HEIGHT;
        }
    }

    private void endSelectionDrag(@SuppressWarnings("unused") int x, @SuppressWarnings("unused") int y) {
        // Hide the selection panel
        selectionPanel.setVisible(false);
    }

    // Resort the existing cards based on the current sort
    public void resort() {
        // First null out the grid and trim it down
        for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
            for (ArrayList<CardView> stack : gridRow) {
                stack.clear();
            }
        }
        trimGrid();

        // First sort all cards by name
        Collections.sort(allCards, new CardViewNameComparator());

        // Now re-insert all of the cards using the current sort
        for (CardView card : allCards) {
            sortIntoGrid(card);
        }

        // Deselect everything
        deselectAll();

        // And finally rerender
        layoutGrid();
        repaint();
    }

    public void reselectBy() {
        // Deselect everything
        deselectAll();

        boolean useText = false;
        String searchStr = "";
        if (searchByTextField.getText().length() >= 3) {
            useText = true;
            searchStr = searchByTextField.getText().toLowerCase();
        }

        for (CardType cardType : selectByTypeButtons.keySet()) {
            AbstractButton button = selectByTypeButtons.get(cardType);
            if (button != null) {
                if (button.isSelected()) {
                    for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
                        for (ArrayList<CardView> stack : gridRow) {
                            for (CardView card : stack) {
                                boolean s = card.isSelected() | card.getCardTypes().contains(cardType);
                                card.setSelected(s);
                                cardViews.get(card.getId()).update(card);
                            }
                        }
                    }
                }
            }
        }

        if (useText) {
            for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
                for (ArrayList<CardView> stack : gridRow) {
                    for (CardView card : stack) {
                        boolean s = card.isSelected();
                        // Name
                        if (!s) {
                            s |= card.getName().toLowerCase().contains(searchStr);
                        }
                        // Sub & Super Types
                        if (!s) {
                            for (String str : card.getSuperTypes()) {
                                s |= str.toLowerCase().contains(searchStr);
                            }
                            for (String str : card.getSubTypes()) {
                                s |= str.toLowerCase().contains(searchStr);
                            }
                        }
                        // Rarity
                        if (!s) {
                            s |= card.getRarity().toString().toLowerCase().contains(searchStr);
                        }
                        // Type line
                        if (!s) {
                            String t = "";
                            for (CardType type : card.getCardTypes()) {
                                t += " " + type.toString();
                            }
                            s |= t.toLowerCase().contains(searchStr);
                        }
                        // Casting cost
                        if (!s) {
                            String mc = "";
                            for (String m : card.getManaCost()) {
                                mc += m;
                            }
                            s |= mc.toLowerCase().contains(searchStr);
                        }
                        // Rules
                        if (!s) {
                            for (String str : card.getRules()) {
                                s |= str.toLowerCase().contains(searchStr);
                            }
                        }
                        card.setSelected(s);
                        cardViews.get(card.getId()).update(card);
                    }
                }
            }
        }

        // And finally rerender
        layoutGrid();
        repaint();
    }

    private static final Pattern pattern = Pattern.compile(".*Add(.*)(\\{[WUBRGXC]\\})(.*)to your mana pool");

    public void analyseDeck() {
        HashMap<String, Integer> qtys = new HashMap<>();
        HashMap<String, Integer> pips = new HashMap<>();
        HashMap<String, Integer> sourcePips = new HashMap<>();
        HashMap<String, Integer> manaCounts = new HashMap<>();
        pips.put("#w}", 0);
        pips.put("#u}", 0);
        pips.put("#b}", 0);
        pips.put("#r}", 0);
        pips.put("#g}", 0);
        pips.put("#c}", 0);
        qtys.put("plains", 0);
        qtys.put("island", 0);
        qtys.put("swamp", 0);
        qtys.put("mountain", 0);
        qtys.put("forest", 0);
        qtys.put("basic", 0);
        qtys.put("wastes", 0);
        manaCounts = new HashMap<>();

        for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
            for (ArrayList<CardView> stack : gridRow) {
                for (CardView card : stack) {
                    // Type line
                    String t = "";
                    for (CardType type : card.getCardTypes()) {
                        t += " " + type.toString();
                    }
                    // Sub & Super Types
                    for (String str : card.getSuperTypes()) {
                        t += " " + str.toLowerCase();
                    }
                    for (String str : card.getSubTypes()) {
                        t += " " + str.toLowerCase();
                    }

                    for (String qty : qtys.keySet()) {
                        int value = qtys.get(qty);
                        if (t.toLowerCase().contains(qty)) {
                            qtys.put(qty, ++value);
                        }

                        // Rules
                        for (String str : card.getRules()) {
                            if (str.toLowerCase().contains(qty)) {
                                qtys.put(qty, ++value);
                            }
                        }
                    }
                    // Wastes (special case)
                    if (card.getName().equals("Wastes")) {
                        int value = qtys.get("wastes");
                        qtys.put("wastes", ++value);
                    }

                    // Mana Cost
                    String mc = "";
                    for (String m : card.getManaCost()) {
                        mc += m;
                    }
                    mc = mc.replaceAll("\\{([WUBRG]).([WUBRG])\\}", "{$1}{$2}");
                    mc = mc.replaceAll("\\{", "#");
                    mc = mc.toLowerCase();
                    for (String pip : pips.keySet()) {
                        int value = pips.get(pip);
                        while (mc.toLowerCase().contains(pip)) {
                            pips.put(pip, ++value);
                            mc = mc.replaceFirst(pip, "");
                        }
                    }

                    // Adding mana
                    for (String str : card.getRules()) {
                        Matcher m = pattern.matcher(str);
                        // ".*Add(.*)(\\{[WUBRGXC]\\})(.*)to your mana pool"
                        while (m.find()) {
                            System.out.println("0=" + m.group(0) + ",,,1=" + m.group(1) + ",,,2=" + m.group(2) + ",,,3=" + m.group(3));
                            str = "Add" + m.group(1) + m.group(3) + "to your mana pool";
                            int num = 1;
                            if (manaCounts.get(m.group(2)) != null) {
                                num = manaCounts.get(m.group(2));
                                num++;
                            }
                            manaCounts.put(m.group(2), num);
                            m = pattern.matcher(str);
                        }
                    }
                }
            }
        }

        String finalInfo = "Found the following quantity of mana costs, mana sources and land types:<br><font size=-1><ul>";
        for (String qty : qtys.keySet()) {
            int value = qtys.get(qty);
            if (value > 0) {
                finalInfo += "<li>" + qty + " = " + value;
            }
        }

        for (String source : sourcePips.keySet()) {
            int value = sourcePips.get(source);
            if (value > 0) {
                finalInfo += "<li>" + "Mana source " + source + " = " + value;
            }
        }

        for (String pip : pips.keySet()) {
            int value = pips.get(pip);
            if (value > 0) {
                finalInfo += "<li>" + pip.toUpperCase() + " mana pip/s = " + value;
            }
        }

        for (String mana : manaCounts.keySet()) {
            int value = manaCounts.get(mana);
            if (value > 0) {
                finalInfo += "<li>" + mana.toUpperCase() + " mana sources = " + value;
            }
        }
        finalInfo = finalInfo.replaceAll("#", "\\{");
        finalInfo += "</ul>";

        MageFrame.getInstance().showMessage(finalInfo);
    }

    // Update the contents of the card grid
    public void setCards(CardsView cardsView, DeckCardLayout layout, BigCard bigCard) {
        if (bigCard != null) {
            lastBigCard = bigCard;
        }

        // Remove all of the cards not in the cardsView
        boolean didModify = false; // Until contested
        for (int i = 0; i < cardGrid.size(); ++i) {
            ArrayList<ArrayList<CardView>> gridRow = cardGrid.get(i);
            for (int j = 0; j < gridRow.size(); ++j) {
                ArrayList<CardView> stack = gridRow.get(j);
                for (int k = 0; k < stack.size(); ++k) {
                    CardView card = stack.get(k);
                    if (!cardsView.containsKey(card.getId())) {
                        // Remove it
                        removeCardView(card);
                        stack.remove(k--);

                        // Mark
                        didModify = true;
                    }
                }
            }
        }

        // Trim the grid
        if (didModify) {
            trimGrid();
        }

        if (layout == null) {
            // No layout -> add any new card views one at a time as par the current sort
            for (CardView newCard : cardsView.values()) {
                if (!cardViews.containsKey(newCard.getId())) {
                    // Is a new card
                    addCardView(newCard, false);

                    // Put it into the appropirate place in the grid given the current sort
                    sortIntoGrid(newCard);

                    // Mark
                    didModify = true;
                }
            }
        } else {
            // Layout given -> Build card grid using layout, and set sort / separate

            // Always modify when given a layout
            didModify = true;

            // Load in settings
            loadSettings(Settings.parse(layout.getSettings()));

            // Traverse the cards once and track them so we can pick ones to insert into the grid
            Map<String, Map<String, ArrayList<CardView>>> trackedCards = new HashMap<>();
            for (CardView newCard : cardsView.values()) {
                if (!cardViews.containsKey(newCard.getId())) {
                    // Add the new card
                    addCardView(newCard, false);

                    // Add the new card to tracking
                    Map<String, ArrayList<CardView>> forSetCode;
                    if (trackedCards.containsKey(newCard.getExpansionSetCode())) {
                        forSetCode = trackedCards.get(newCard.getExpansionSetCode());
                    } else {
                        forSetCode = new HashMap<>();
                        trackedCards.put(newCard.getExpansionSetCode(), forSetCode);
                    }
                    ArrayList<CardView> list;
                    if (forSetCode.containsKey(newCard.getCardNumber())) {
                        list = forSetCode.get(newCard.getCardNumber());
                    } else {
                        list = new ArrayList<>();
                        forSetCode.put(newCard.getCardNumber(), list);
                    }
                    list.add(newCard);
                }
            }

            // Now go through the layout and use it to build the cardGrid
            cardGrid = new ArrayList<>();
            maxStackSize = new ArrayList<>();
            for (List<List<DeckCardInfo>> row : layout.getCards()) {
                ArrayList<ArrayList<CardView>> gridRow = new ArrayList<>();
                int thisMaxStackSize = 0;
                cardGrid.add(gridRow);
                for (List<DeckCardInfo> stack : row) {
                    ArrayList<CardView> gridStack = new ArrayList<>();
                    gridRow.add(gridStack);
                    for (DeckCardInfo info : stack) {
                        if (trackedCards.containsKey(info.getSetCode()) && trackedCards.get(info.getSetCode()).containsKey(info.getCardNum())) {
                            ArrayList<CardView> candidates
                                    = trackedCards.get(info.getSetCode()).get(info.getCardNum());
                            if (candidates.size() > 0) {
                                gridStack.add(candidates.remove(0));
                                thisMaxStackSize = Math.max(thisMaxStackSize, gridStack.size());
                            }
                        }
                    }
                }
                maxStackSize.add(thisMaxStackSize);
            }

            // Check that there aren't any "orphans" not referenced in the layout. There should
            // never be any under normal operation, but as a failsafe in case the user screwed with
            // the file in an invalid way, sort them into the grid so that they aren't just left hanging.
            for (Map<String, ArrayList<CardView>> tracked : trackedCards.values()) {
                for (ArrayList<CardView> orphans : tracked.values()) {
                    for (CardView orphan : orphans) {
                        LOGGER.info("Orphan when setting with layout: ");
                        sortIntoGrid(orphan);
                    }
                }
            }
        }

        // Modifications?
        if (didModify) {
            // Update layout
            layoutGrid();

            // Update draw
            cardScroll.revalidate();
            repaint();
        }
    }

    private int getCount(CardType cardType) {
        if (null != cardType) {
            switch (cardType) {
                case CREATURE:
                    return creatureCounter.get();
                case LAND:
                    return landCounter.get();
                case ARTIFACT:
                    return artifactCounter.get();
                case ENCHANTMENT:
                    return enchantmentCounter.get();
                case INSTANT:
                    return instantCounter.get();
                case PLANESWALKER:
                    return planeswalkerCounter.get();
                case SORCERY:
                    return sorceryCounter.get();
                case TRIBAL:
                    return tribalCounter.get();
                default:
                    break;
            }
        }
        return 0;
    }

    private void updateCounts() {
        deckNameAndCountLabel.setText(role.getName() + " - " + allCards.size());
        creatureCountLabel.setText("" + creatureCounter.get());
        landCountLabel.setText("" + landCounter.get());
        for (CardType cardType : selectByTypeButtons.keySet()) {
            AbstractButton button = selectByTypeButtons.get(cardType);
            String text = cardType.toString();
            int numCards = getCount(cardType);
            if (numCards > 0) {
                button.setForeground(Color.BLACK);
                text = text + " - " + numCards;
            } else {
                button.setForeground(new Color(100, 100, 100));
            }
            button.setText(text);
        }
    }

    private void showCardRightClickMenu(@SuppressWarnings("unused") final CardView card, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem hide = new JMenuItem("Hide");
        hide.addActionListener(e2 -> hideSelection());
        menu.add(hide);

        // Show 'Duplicate Selection' for FREE_BUILDING
        if (this.mode == Constants.DeckEditorMode.FREE_BUILDING) {
            JMenuItem duplicateSelection = new JMenuItem("Duplicate Selection");
            duplicateSelection.addActionListener(e2 -> duplicateSelection());
            menu.add(duplicateSelection);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    public void addCardView(final CardView card, boolean duplicated) {
        allCards.add(card);

        // Update counts
        for (CardTypeCounter counter : allCounters) {
            counter.add(card);
        }
        updateCounts();

        // Create the card view
        final MageCard cardPanel = Plugins.getInstance().getMageCard(card, lastBigCard, new Dimension(getCardWidth(), getCardHeight()), null, true, true);
        cardPanel.update(card);
        cardPanel.setTextOffset(0);

        // Remove mouse wheel listeners so that scrolling works
        // Scrolling works on all areas without cards or by using the scroll bar, that's enough
//        for (MouseWheelListener l : cardPanel.getMouseWheelListeners()) {
//            cardPanel.removeMouseWheelListener(l);
//        }
        // Add a click listener for selection / drag start
        cardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // Select if not selected
                    if (!card.isSelected()) {
                        selectCard(card);
                    }
                    // Show menu
                    showCardRightClickMenu(card, e);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.getClickCount() == 1) {
                        cardClicked(card, e);
                    } else if (e.isAltDown()) {
                        eventSource.altDoubleClick(card, "alt-double-click");
                    } else {
                        eventSource.doubleClick(card, "double-click");
                    }
                }
            }
        });

        // Add a motion listener to process drags
        cardPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragger.isDragging()) {
                    // If the card isn't already selected, make sure it is
                    if (!card.isSelected()) {
                        cardClicked(card, e);
                    }
                    dragger.beginDrag(cardPanel, e);
                }
            }
        });

        // And add it
        cardContent.add(cardPanel);
        cardViews.put(card.getId(), cardPanel);

        if (duplicated) {
            sortIntoGrid(card);
            eventSource.addSpecificCard(card, "add-specific-card");
            // Update layout
            layoutGrid();
            // Update draw
            cardScroll.revalidate();
            repaint();
        }
    }

    private final ArrayList<DragCardGridListener> listeners = new ArrayList<>();

    public void addDragCardGridListener(DragCardGridListener l) {
        listeners.add(l);
    }

    private void notifyCardsSelected() {
        for (DragCardGridListener listener : listeners) {
            listener.cardsSelected();
        }
    }

    private void selectCard(CardView targetCard) {
        // Set the selected card to the target card
        for (CardView card : allCards) {
            if (card == targetCard) {
                if (!card.isSelected()) {
                    card.setSelected(true);
                    cardViews.get(card.getId()).update(card);
                }
            } else if (card.isSelected()) {
                card.setSelected(false);
                cardViews.get(card.getId()).update(card);
            }
        }
    }

    private void toggleSelected(CardView targetCard) {
        targetCard.setSelected(!targetCard.isSelected());
        cardViews.get(targetCard.getId()).update(targetCard);
    }

    private void cardClicked(CardView targetCard, MouseEvent e) {
        if (e.isShiftDown()) {
            toggleSelected(targetCard);
        } else {
            selectCard(targetCard);
        }
        notifyCardsSelected();
    }

    private void removeCardView(CardView card) {
        allCards.remove(card);

        // Remove fromcounts
        for (CardTypeCounter counter : allCounters) {
            counter.remove(card);
        }
        updateCounts();

        cardContent.remove(cardViews.get(card.getId()));
        cardViews.remove(card.getId());
    }

    /**
     * Add a card to the cardGrid, in the position that the current sort
     * dictates
     *
     * @param newCard Card to add to the cardGrid array.
     */
    private void sortIntoGrid(CardView newCard) {
        // Ensure row 1 exists
        if (cardGrid.isEmpty()) {
            cardGrid.add(0, new ArrayList<>());
            maxStackSize.add(0, 0);
        }
        // What row to add it to?
        ArrayList<ArrayList<CardView>> targetRow;
        if (separateCreatures && !newCard.getCardTypes().contains(CardType.CREATURE)) {
            // Ensure row 2 exists
            if (cardGrid.size() < 2) {
                cardGrid.add(1, new ArrayList<>());
                maxStackSize.add(1, 0);
                // Populate with stacks matching the first row
                for (int i = 0; i < cardGrid.get(0).size(); ++i) {
                    cardGrid.get(1).add(new ArrayList<>());
                }
            }
            targetRow = cardGrid.get(1);
        } else {
            targetRow = cardGrid.get(0);
        }

        // Find the right column to insert into
        boolean didInsert = false;
        for (int currentColumn = 0; currentColumn < cardGrid.get(0).size(); ++currentColumn) {
            // Find an item from this column
            CardView cardInColumn = null;
            for (ArrayList<ArrayList<CardView>> gridRow : cardGrid) {
                for (CardView card : gridRow.get(currentColumn)) {
                    cardInColumn = card;
                    break;
                }
            }

            // No card in this column?
            if (cardInColumn == null) {
                // Error, should not have an empty column
                LOGGER.error("Empty column! " + currentColumn);
            } else {
                int res = cardSort.getComparator().compare(newCard, cardInColumn);
                if (res <= 0) {
                    // Insert into this col, but if less, then we need to create a new col here first
                    if (res < 0) {
                        for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
                            cardGrid.get(rowIndex).add(currentColumn, new ArrayList<>());
                        }
                    }
                    targetRow.get(currentColumn).add(newCard);
                    didInsert = true;
                    break;
                } else {
                    // Nothing to do, go to next iteration
                }
            }
        }

        // If nothing else, insert in a new column after everything else
        if (!didInsert) {
            for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
                cardGrid.get(rowIndex).add(new ArrayList<>());
            }
            targetRow.get(targetRow.size() - 1).add(newCard);
        }
    }

    /**
     * Delete any empty columns / rows from the grid, and eleminate any empty
     * space in stacks
     */
    private void trimGrid() {
        // Compact stacks and rows
        for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
            ArrayList<ArrayList<CardView>> gridRow = cardGrid.get(rowIndex);
            int rowMaxStackSize = 0;
            for (ArrayList<CardView> stack : gridRow) {
                // Clear out nulls in the stack
                for (int i = 0; i < stack.size(); ++i) {
                    if (stack.get(i) == null) {
                        stack.remove(i--);
                    }
                }
                // Is the stack still non-empty?
                rowMaxStackSize = Math.max(rowMaxStackSize, stack.size());
            }
            // Is the row empty? If so remove it
            if (rowMaxStackSize == 0) {
                cardGrid.remove(rowIndex);
                maxStackSize.remove(rowIndex);
                --rowIndex;
            } else {
                maxStackSize.set(rowIndex, rowMaxStackSize);
            }
        }

        // Remove empty columns
        if (!cardGrid.isEmpty()) {
            for (int colIndex = 0; colIndex < cardGrid.get(0).size(); ++colIndex) {
                boolean hasContent = false; // Until contested
                for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
                    if (!cardGrid.get(rowIndex).get(colIndex).isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
                if (!hasContent) {
                    for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
                        cardGrid.get(rowIndex).remove(colIndex);
                    }
                    --colIndex;
                }
            }
        }

        // Clean up extra column header count labels
        while (stackCountLabels.size() > cardGrid.size()) {
            ArrayList<JLabel> labels = stackCountLabels.remove(cardGrid.size());
            for (JLabel label : labels) {
                cardContent.remove(label);
            }
        }
        int colCount = cardGrid.isEmpty() ? 0 : cardGrid.get(0).size();
        for (ArrayList<JLabel> labels : stackCountLabels) {
            while (labels.size() > colCount) {
                cardContent.remove(labels.remove(colCount));
            }
        }
    }

    private int getCardWidth() {
        return (int) (GUISizeHelper.editorCardDimension.width * cardSizeMod);
    }

    private int getCardHeight() {
        return (int) (1.4 * getCardWidth());
    }

    /**
     * Position all of the card views correctly
     */
    private void layoutGrid() {
        // Basic dimensions
        int cardWidth = getCardWidth();
        int cardHeight = getCardHeight();
        int cardTopHeight = CardRenderer.getCardTopHeight(cardWidth);

        // Layout one at a time
        int layerIndex = 0;
        int currentY = COUNT_LABEL_HEIGHT;
        int maxWidth = 0;
        for (int rowIndex = 0; rowIndex < cardGrid.size(); ++rowIndex) {
            int rowMaxStackSize = 0;
            ArrayList<ArrayList<CardView>> gridRow = cardGrid.get(rowIndex);
            for (int colIndex = 0; colIndex < gridRow.size(); ++colIndex) {
                ArrayList<CardView> stack = gridRow.get(colIndex);

                // Stack count label
                if (stackCountLabels.size() <= rowIndex) {
                    stackCountLabels.add(new ArrayList<>());
                }
                if (stackCountLabels.get(rowIndex).size() <= colIndex) {
                    JLabel countLabel = new JLabel("", SwingConstants.CENTER);
                    countLabel.setForeground(Color.WHITE);
                    cardContent.add(countLabel, new Integer(0));
                    stackCountLabels.get(rowIndex).add(countLabel);
                }
                JLabel countLabel = stackCountLabels.get(rowIndex).get(colIndex);
                if (stack.isEmpty()) {
                    countLabel.setVisible(false);
                } else {
                    countLabel.setText("" + stack.size());
                    countLabel.setLocation(GRID_PADDING + (cardWidth + GRID_PADDING) * colIndex, currentY - COUNT_LABEL_HEIGHT);
                    countLabel.setSize(cardWidth, COUNT_LABEL_HEIGHT);
                    countLabel.setVisible(true);
                }

                // Max stack size
                rowMaxStackSize = Math.max(rowMaxStackSize, stack.size());

                // Layout cards in stack
                for (int i = 0; i < stack.size(); ++i) {
                    CardView card = stack.get(i);
                    MageCard view = cardViews.get(card.getId());
                    int x = GRID_PADDING + (cardWidth + GRID_PADDING) * colIndex;
                    int y = currentY + i * cardTopHeight;
                    view.setCardBounds(x, y, cardWidth, cardHeight);
                    cardContent.setLayer(view, layerIndex++);
                }
            }

            // Update the max stack size for this row and the max width
            maxWidth = Math.max(maxWidth, GRID_PADDING + (GRID_PADDING + cardWidth) * gridRow.size());
            maxStackSize.set(rowIndex, rowMaxStackSize);
            currentY += (cardTopHeight * (rowMaxStackSize - 1) + cardHeight) + COUNT_LABEL_HEIGHT;
        }

        // Resize card container
        cardContent.setPreferredSize(new Dimension(maxWidth, currentY - COUNT_LABEL_HEIGHT + GRID_PADDING));
        //cardContent.setSize(maxWidth, currentY - COUNT_LABEL_HEIGHT + GRID_PADDING);
    }

    private static void makeButtonPopup(final AbstractButton button, final JPopupMenu popup) {
        button.addActionListener(e -> popup.show(button, 0, button.getHeight()));
    }
}

/**
 * Note: This class can't just be a JPanel, because a JPanel doesn't draw when
 * it has Opaque = false, but this class needs to go into a JLayeredPane while
 * being translucent, so it NEEDS Opaque = false in order to behave correctly.
 * Thus this simple class is needed to implement a translucent box in a
 * JLayeredPane.
 */
class SelectionBox extends JComponent {

    public SelectionBox() {
        setOpaque(false);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g = g.create();
        g.setColor(new Color(100, 100, 200, 128));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(0, 0, 255));
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g.dispose();
    }
}
