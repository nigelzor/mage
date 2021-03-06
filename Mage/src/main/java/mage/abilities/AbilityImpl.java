/*
 * Copyright 2010 BetaSteward_at_googlemail.com. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY BetaSteward_at_googlemail.com ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL BetaSteward_at_googlemail.com OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of BetaSteward_at_googlemail.com.
 */
package mage.abilities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mage.MageObject;
import mage.MageObjectReference;
import mage.Mana;
import mage.abilities.costs.AdjustingSourceCosts;
import mage.abilities.costs.AlternativeSourceCosts;
import mage.abilities.costs.Cost;
import mage.abilities.costs.Costs;
import mage.abilities.costs.CostsImpl;
import mage.abilities.costs.OptionalAdditionalSourceCosts;
import mage.abilities.costs.VariableCost;
import mage.abilities.costs.common.TapSourceCost;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.costs.mana.ManaCosts;
import mage.abilities.costs.mana.ManaCostsImpl;
import mage.abilities.costs.mana.VariableManaCost;
import mage.abilities.effects.ContinuousEffect;
import mage.abilities.effects.Effect;
import mage.abilities.effects.Effects;
import mage.abilities.effects.OneShotEffect;
import mage.abilities.effects.common.BasicManaEffect;
import mage.abilities.effects.common.DynamicManaEffect;
import mage.abilities.keyword.FlashbackAbility;
import mage.abilities.mana.ManaAbility;
import mage.cards.Card;
import mage.constants.AbilityType;
import mage.constants.AbilityWord;
import mage.constants.EffectType;
import mage.constants.Outcome;
import mage.constants.SpellAbilityType;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.command.Emblem;
import mage.game.events.GameEvent;
import mage.game.events.ManaEvent;
import mage.game.permanent.Permanent;
import mage.game.stack.Spell;
import mage.game.stack.StackAbility;
import mage.players.Player;
import mage.target.Target;
import mage.target.Targets;
import mage.util.GameLog;
import mage.util.ThreadLocalStringBuilder;
import mage.watchers.Watcher;
import org.apache.log4j.Logger;

/**
 *
 * @author BetaSteward_at_googlemail.com
 */
public abstract class AbilityImpl implements Ability {

    private static final Logger logger = Logger.getLogger(AbilityImpl.class);
    private static final ThreadLocalStringBuilder threadLocalBuilder = new ThreadLocalStringBuilder(100);
    private static final List<Watcher> emptyWatchers = new ArrayList<>();
    private static final List<Ability> emptyAbilities = new ArrayList<>();

    protected UUID id;
    protected UUID originalId;
    protected AbilityType abilityType;
    protected UUID controllerId;
    protected UUID sourceId;
    protected ManaCosts<ManaCost> manaCosts;
    protected ManaCosts<ManaCost> manaCostsToPay;
    protected Costs<Cost> costs;
    protected Costs<Cost> optionalCosts;
    protected Modes modes;
    protected Zone zone;
    protected String name;
    protected AbilityWord abilityWord;
    protected boolean usesStack = true;
    protected boolean ruleAtTheTop = false;
    protected boolean ruleVisible = true;
    protected boolean ruleAdditionalCostsVisible = true;
    protected boolean costModificationActive = true;
    protected boolean activated = false;
    protected boolean worksFaceDown = false;
    protected MageObject sourceObject;
    protected int sourceObjectZoneChangeCounter;
    protected List<Watcher> watchers = null;
    protected List<Ability> subAbilities = null;
    protected boolean canFizzle = true;

    public AbilityImpl(AbilityType abilityType, Zone zone) {
        this.id = UUID.randomUUID();
        this.originalId = id;
        this.abilityType = abilityType;
        this.zone = zone;
        this.manaCosts = new ManaCostsImpl<>();
        this.manaCostsToPay = new ManaCostsImpl<>();
        this.costs = new CostsImpl<>();
        this.optionalCosts = new CostsImpl<>();
        this.modes = new Modes();
    }

    public AbilityImpl(final AbilityImpl ability) {
        this.id = ability.id;
        this.originalId = ability.originalId;
        this.abilityType = ability.abilityType;
        this.controllerId = ability.controllerId;
        this.sourceId = ability.sourceId;
        this.zone = ability.zone;
        this.name = ability.name;
        this.usesStack = ability.usesStack;
        this.manaCosts = ability.manaCosts;
        this.manaCostsToPay = ability.manaCostsToPay.copy();
        this.costs = ability.costs.copy();
        this.optionalCosts = ability.optionalCosts.copy();
        if (ability.watchers != null) {
            this.watchers = new ArrayList<>();
            for (Watcher watcher : ability.watchers) {
                watchers.add(watcher.copy());
            }
        }
        if (ability.subAbilities != null) {
            this.subAbilities = new ArrayList<>();
            for (Ability subAbility : ability.subAbilities) {
                subAbilities.add(subAbility.copy());
            }
        }
        this.modes = ability.getModes().copy();
        this.ruleAtTheTop = ability.ruleAtTheTop;
        this.ruleVisible = ability.ruleVisible;
        this.ruleAdditionalCostsVisible = ability.ruleAdditionalCostsVisible;
        this.costModificationActive = ability.costModificationActive;
        this.worksFaceDown = ability.worksFaceDown;
        this.abilityWord = ability.abilityWord;
        this.sourceObject = ability.sourceObject;
        this.sourceObjectZoneChangeCounter = ability.sourceObjectZoneChangeCounter;
        this.canFizzle = ability.canFizzle;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void newId() {
        if (!(this instanceof MageSingleton)) {
            this.id = UUID.randomUUID();
        }
        getEffects().newId();
    }

    @Override
    public void newOriginalId() {
        this.id = UUID.randomUUID();
        this.originalId = id;
        getEffects().newId();
    }

    @Override
    public AbilityType getAbilityType() {
        return this.abilityType;
    }

    @Override
    public boolean resolve(Game game) {
        boolean result = true;
        //20100716 - 117.12
        if (checkIfClause(game)) {

            for (Effect effect : getEffects()) {
                if (effect instanceof OneShotEffect) {
                    boolean effectResult = effect.apply(game, this);
                    result &= effectResult;
                    if (logger.isDebugEnabled()) {
                        if (!this.getAbilityType().equals(AbilityType.MANA)) {
                            if (!effectResult) {
                                if (this.getSourceId() != null) {
                                    MageObject mageObject = game.getObject(this.getSourceId());
                                    if (mageObject != null) {
                                        logger.debug("AbilityImpl.resolve: object: " + mageObject.getName());
                                    }
                                }
                                logger.debug("AbilityImpl.resolve: effect returned false -" + effect.getText(this.getModes().getMode()));
                            }
                        }
                    }
                } else {
                    game.addEffect((ContinuousEffect) effect, this);
                }
                /**
                 * All restrained trigger events are fired now. To restrain the
                 * events is mainly neccessary because of the movement of
                 * multiple object at once. If the event is fired directly as
                 * one object moved, other objects are not already in the
                 * correct zone to check for their effects. (e.g. Valakut, the
                 * Molten Pinnacle)
                 */
                game.getState().handleSimultaneousEvent(game);
                game.resetShortLivingLKI();
                /**
                 * game.applyEffects() has to be done at least for every effect
                 * that moves cards/permanent between zones, or changes control
                 * of objects so Static effects work as intened if dependant
                 * from the moved objects zone it is in Otherwise for example
                 * were static abilities with replacement effects deactivated
                 * too late Example:
                 * {@link org.mage.test.cards.replacement.DryadMilitantTest#testDiesByDestroy testDiesByDestroy}
                 */
                if (effect.applyEffectsAfter()) {
                    game.applyEffects();
                    game.getState().getTriggers().checkStateTriggers(game);
                }
            }
        }
        return result;
    }

    @Override
    public boolean activate(Game game, boolean noMana) {
        Player controller = game.getPlayer(this.getControllerId());
        if (controller == null) {
            return false;
        }
        game.applyEffects();

        /* 20130201 - 601.2b
         * If the spell is modal the player announces the mode choice (see rule 700.2).
         */
        if (!getModes().choose(game, this)) {
            return false;
        }

        getSourceObject(game);

        if (controller.isTestMode()) {
            if (!controller.addTargets(this, game)) {
                return false;
            }
        }

        /* 20130201 - 601.2b
         * If the player wishes to splice any cards onto the spell (see rule 702.45), he
         * or she reveals those cards in his or her hand.
         */
        if (this.abilityType.equals(AbilityType.SPELL)) {
            game.getContinuousEffects().applySpliceEffects(this, game);
        }

        // if ability can be cast for no mana, clear the mana costs now, because additional mana costs must be paid.
        // For Flashback ability can be set X before, so the X costs have to be restored for the flashbacked ability
        if (noMana) {
            if (this.getManaCostsToPay().getVariableCosts().size() > 0) {
                int xValue = this.getManaCostsToPay().getX();
                this.getManaCostsToPay().clear();
                VariableManaCost xCosts = new VariableManaCost();
                xCosts.setAmount(xValue);
                this.getManaCostsToPay().add(xCosts);
            } else {
                this.getManaCostsToPay().clear();
            }
        }
        // 20130201 - 601.2b
        // If the spell has alternative or additional costs that will be paid as it's being cast such
        // as buyback, kicker, or convoke costs (see rules 117.8 and 117.9), the player announces his
        // or her intentions to pay any or all of those costs (see rule 601.2e).
        // A player can't apply two alternative methods of casting or two alternative costs to a single spell.
        if (!activateAlternateOrAdditionalCosts(sourceObject, noMana, controller, game)) {
            if (getAbilityType().equals(AbilityType.SPELL)
                    && ((SpellAbility) this).getSpellAbilityType().equals(SpellAbilityType.FACE_DOWN_CREATURE)) {
                return false;
            }
        }

        // 20121001 - 601.2b
        // If the spell has a variable cost that will be paid as it's being cast (such as an {X} in
        // its mana cost; see rule 107.3), the player announces the value of that variable.
        VariableManaCost variableManaCost = handleManaXCosts(game, noMana, controller);
        String announceString = handleOtherXCosts(game, controller);
        // For effects from cards like Void Winnower x costs have to be set
        if (this.getAbilityType().equals(AbilityType.SPELL)
                && game.replaceEvent(GameEvent.getEvent(GameEvent.EventType.CAST_SPELL_LATE, getId(), getSourceId(), getControllerId()), this)) {
            return false;
        }
        for (UUID modeId : this.getModes().getSelectedModes()) {
            this.getModes().setActiveMode(modeId);
            //20121001 - 601.2c
            // 601.2c The player announces his or her choice of an appropriate player, object, or zone for
            // each target the spell requires. A spell may require some targets only if an alternative or
            // additional cost (such as a buyback or kicker cost), or a particular mode, was chosen for it;
            // otherwise, the spell is cast as though it did not require those targets. If the spell has a
            // variable number of targets, the player announces how many targets he or she will choose before
            // he or she announces those targets. The same target can't be chosen multiple times for any one
            // instance of the word "target" on the spell. However, if the spell uses the word "target" in
            // multiple places, the same object, player, or zone can be chosen once for each instance of the
            // word "target" (as long as it fits the targeting criteria). If any effects say that an object
            // or player must be chosen as a target, the player chooses targets so that he or she obeys the
            // maximum possible number of such effects without violating any rules or effects that say that
            // an object or player can't be chosen as a target. The chosen players, objects, and/or zones
            // each become a target of that spell. (Any abilities that trigger when those players, objects,
            // and/or zones become the target of a spell trigger at this point; they'll wait to be put on
            // the stack until the spell has finished being cast.)

            if (sourceObject != null && !this.getAbilityType().equals(AbilityType.TRIGGERED)) { // triggered abilities check this already in playerImpl.triggerAbility
                sourceObject.adjustTargets(this, game);
            }
            // Flashback abilities haven't made the choices the underlying spell might need for targeting.
            if (!(this instanceof FlashbackAbility)
                    && getTargets().size() > 0) {
                Outcome outcome = getEffects().isEmpty() ? Outcome.Detriment : getEffects().get(0).getOutcome();
                if (getTargets().chooseTargets(outcome, this.controllerId, this, noMana, game) == false) {
                    if ((variableManaCost != null || announceString != null) && !game.isSimulation()) {
                        game.informPlayer(controller, (sourceObject != null ? sourceObject.getIdName() : "") + ": no valid targets with this value of X");
                    }
                    return false; // when activation of ability is canceled during target selection
                }
            }
        } // end modes

        // TODO: Handle optionalCosts at the same time as already OptionalAdditionalSourceCosts are handled.
        for (Cost cost : optionalCosts) {
            if (cost instanceof ManaCost) {
                cost.clearPaid();
                if (controller.chooseUse(Outcome.Benefit, "Pay optional cost " + cost.getText() + "?", this, game)) {
                    manaCostsToPay.add((ManaCost) cost);
                }
            }
        }
        //20100716 - 601.2e
        if (sourceObject != null) {
            sourceObject.adjustCosts(this, game);
            if (sourceObject instanceof Card) {
                for (Ability ability : ((Card) sourceObject).getAbilities(game)) {
                    if (ability instanceof AdjustingSourceCosts) {
                        ((AdjustingSourceCosts) ability).adjustCosts(this, game);
                    }
                }
            } else {
                for (Ability ability : sourceObject.getAbilities()) {
                    if (ability instanceof AdjustingSourceCosts) {
                        ((AdjustingSourceCosts) ability).adjustCosts(this, game);
                    }
                }
            }
        }

        // this is a hack to prevent mana abilities with mana costs from causing endless loops - pay other costs first
        if (this instanceof ManaAbility && !costs.pay(this, game, sourceId, controllerId, noMana, null)) {
            logger.debug("activate mana ability failed - non mana costs");
            return false;
        }

        //20101001 - 601.2e
        if (costModificationActive) {
            game.getContinuousEffects().costModification(this, game);
        } else {
            costModificationActive = true;
        }

        UUID activatorId = controllerId;
        if ((this instanceof ActivatedAbilityImpl) && ((ActivatedAbilityImpl) this).getActivatorId() != null) {
            activatorId = ((ActivatedAbilityImpl) this).getActivatorId();
        }

        //20100716 - 601.2f  (noMana is not used here, because mana costs were cleared for this ability before adding additional costs and applying cost modification effects)
        if (!manaCostsToPay.pay(this, game, sourceId, activatorId, false, null)) {
            return false; // cancel during mana payment
        }

        //20100716 - 601.2g
        if (!costs.pay(this, game, sourceId, activatorId, noMana, null)) {
            logger.debug("activate failed - non mana costs");
            return false;
        }
        if (!game.isSimulation()) {
            // inform about x costs now, so canceled announcements are not shown in the log
            if (announceString != null) {
                game.informPlayers(announceString);
            }
            if (variableManaCost != null) {
                int xValue = getManaCostsToPay().getX();
                game.informPlayers(controller.getLogName() + " announces a value of " + xValue + " for " + variableManaCost.getText());
            }
        }
        activated = true;
        // fire if tapped for mana (may only fire now because else costs of ability itself can be payed with mana of abilities that trigger for that event
        if (this.getAbilityType().equals(AbilityType.MANA)) {
            for (Cost cost : costs) {
                if (cost instanceof TapSourceCost) {
                    Mana mana = null;
                    Effect effect = getEffects().get(0);
                    if (effect instanceof DynamicManaEffect) {
                        mana = ((DynamicManaEffect) effect).getMana(game, this);
                    } else if (effect instanceof BasicManaEffect) {
                        mana = ((BasicManaEffect) effect).getMana(game, this);
                    }
                    if (mana != null && mana.getAny() == 0) { // if mana == null or Any > 0 the event has to be fired in the mana effect to know which mana was produced
                        ManaEvent event = new ManaEvent(GameEvent.EventType.TAPPED_FOR_MANA, sourceId, sourceId, controllerId, mana);
                        if (!game.replaceEvent(event)) {
                            game.fireEvent(event);
                        }
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public boolean activateAlternateOrAdditionalCosts(MageObject sourceObject, boolean noMana, Player controller, Game game) {
        boolean alternativeCostisUsed = false;
        if (sourceObject != null && !(sourceObject instanceof Permanent) && !(this instanceof FlashbackAbility)) {
            Abilities<Ability> abilities = null;
            if (sourceObject instanceof Card) {
                abilities = ((Card) sourceObject).getAbilities(game);
            } else {
                sourceObject.getAbilities();
            }
            if (abilities != null) {
                for (Ability ability : abilities) {
                    // if cast for noMana no Alternative costs are allowed
                    if (!noMana && ability instanceof AlternativeSourceCosts) {
                        AlternativeSourceCosts alternativeSpellCosts = (AlternativeSourceCosts) ability;
                        if (alternativeSpellCosts.isAvailable(this, game)) {
                            if (alternativeSpellCosts.askToActivateAlternativeCosts(this, game)) {
                                // only one alternative costs may be activated
                                alternativeCostisUsed = true;
                                break;
                            }
                        }
                    }
                    if (ability instanceof OptionalAdditionalSourceCosts) {
                        ((OptionalAdditionalSourceCosts) ability).addOptionalAdditionalCosts(this, game);
                    }
                }
            }
            // controller specific alternate spell costs
            if (!noMana && !alternativeCostisUsed) {
                if (this.getAbilityType().equals(AbilityType.SPELL)
                        // 117.9a Only one alternative cost can be applied to any one spell as it’s being cast.
                        // So an alternate spell ability can't be paid with Omniscience
                        && !((SpellAbility) this).getSpellAbilityType().equals(SpellAbilityType.BASE_ALTERNATE)) {
                    for (AlternativeSourceCosts alternativeSourceCosts : controller.getAlternativeSourceCosts()) {
                        if (alternativeSourceCosts.isAvailable(this, game)) {
                            if (alternativeSourceCosts.askToActivateAlternativeCosts(this, game)) {
                                // only one alternative costs may be activated
                                alternativeCostisUsed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return alternativeCostisUsed;
    }

    /**
     * Handles the setting of non mana X costs
     *
     * @param controller
     *
     * @param game
     * @return announce message
     *
     */
    protected String handleOtherXCosts(Game game, Player controller) {
        String announceString = null;
        for (VariableCost variableCost : this.costs.getVariableCosts()) {
            if (!(variableCost instanceof VariableManaCost)) {
                int xValue = variableCost.announceXValue(this, game);
                Cost fixedCost = variableCost.getFixedCostsFromAnnouncedValue(xValue);
                if (fixedCost != null) {
                    costs.add(fixedCost);
                }
                // set the xcosts to paid
                variableCost.setAmount(xValue);
                ((Cost) variableCost).setPaid();
                String message = controller.getLogName() + " announces a value of " + xValue + " (" + variableCost.getActionText() + ")";
                if (announceString == null) {
                    announceString = message;
                } else {
                    announceString = announceString + " " + message;
                }
            }
        }
        return announceString;
    }

    /**
     * Handles X mana costs and sets manaCostsToPay.
     *
     * @param game
     * @param noMana
     * @param controller
     * @return variableManaCost for posting to log later
     */
    protected VariableManaCost handleManaXCosts(Game game, boolean noMana, Player controller) {
        // 20121001 - 601.2b
        // If the spell has a variable cost that will be paid as it's being cast (such as an {X} in
        // its mana cost; see rule 107.3), the player announces the value of that variable.
        // TODO: Handle announcing other variable costs here like: RemoveVariableCountersSourceCost
        VariableManaCost variableManaCost = null;
        for (ManaCost cost : manaCostsToPay) {
            if (cost instanceof VariableManaCost) {
                variableManaCost = (VariableManaCost) cost;
                break; // only one VariableManCost per spell (or is it possible to have more?)
            }
        }
        if (variableManaCost != null) {
            int xValue;
            if (!variableManaCost.isPaid()) { // should only happen for human players
                if (!noMana) {
                    xValue = controller.announceXMana(variableManaCost.getMinX(), variableManaCost.getMaxX(), "Announce the value for " + variableManaCost.getText(), game, this);
                    int amountMana = xValue * variableManaCost.getMultiplier();
                    StringBuilder manaString = threadLocalBuilder.get();
                    if (variableManaCost.getFilter() == null || variableManaCost.getFilter().isGeneric()) {
                        manaString.append("{").append(amountMana).append("}");
                    } else {
                        String manaSymbol = null;
                        if (variableManaCost.getFilter().isBlack()) {
                            manaSymbol = "B";
                        } else if (variableManaCost.getFilter().isRed()) {
                            manaSymbol = "R";
                        } else if (variableManaCost.getFilter().isBlue()) {
                            manaSymbol = "U";
                        } else if (variableManaCost.getFilter().isGreen()) {
                            manaSymbol = "G";
                        } else if (variableManaCost.getFilter().isWhite()) {
                            manaSymbol = "W";
                        }
                        if (manaSymbol == null) {
                            throw new UnsupportedOperationException("ManaFilter is not supported: " + this.toString());
                        }
                        for (int i = 0; i < amountMana; i++) {
                            manaString.append("{").append(manaSymbol).append("}");
                        }
                    }
                    manaCostsToPay.add(new ManaCostsImpl(manaString.toString()));
                    manaCostsToPay.setX(amountMana);
                }
                variableManaCost.setPaid();
            }
        }

        return variableManaCost;
    }

    // called at end of turn for each Permanent
    @Override
    public void reset(Game game) {
    }

    @Override
    public boolean checkIfClause(Game game) {
        return true;
    }

    @Override
    public UUID getControllerId() {
        return controllerId;
    }

    @Override
    public void setControllerId(UUID controllerId) {
        this.controllerId = controllerId;
        if (watchers != null) {
            for (Watcher watcher : watchers) {
                watcher.setControllerId(controllerId);
            }
        }
        if (subAbilities != null) {
            for (Ability subAbility : subAbilities) {
                subAbility.setControllerId(controllerId);
            }
        }
    }

    @Override
    public UUID getSourceId() {
        return sourceId;
    }

    @Override
    public void setSourceId(UUID sourceId) {
        if (this.sourceId == null) {
            this.sourceId = sourceId;
        } else if (!(this instanceof MageSingleton)) {
            this.sourceId = sourceId;
        }
        if (subAbilities != null) {
            for (Ability subAbility : subAbilities) {
                subAbility.setSourceId(sourceId);
            }
        }
        if (watchers != null) {
            for (Watcher watcher : watchers) {
                watcher.setSourceId(sourceId);
            }
        }
    }

    @Override
    public Costs<Cost> getCosts() {
        return costs;
    }

    @Override
    public ManaCosts<ManaCost> getManaCosts() {
        return manaCosts;
    }

    /**
     * Should be used by
     * {@link mage.abilities.effects.CostModificationEffect cost modification effects}
     * to manipulate what is actually paid before resolution.
     *
     * @return
     */
    @Override
    public ManaCosts<ManaCost> getManaCostsToPay() {
        return manaCostsToPay;
    }

    @Override
    public Costs<Cost> getOptionalCosts() {
        return optionalCosts;
    }

    @Override
    public Effects getEffects() {
        return getModes().getMode().getEffects();
    }

    @Override
    public Effects getAllEffects() {
        Effects allEffects = new Effects();
        for (Mode mode : getModes().values()) {
            allEffects.addAll(mode.getEffects());
        }
        return allEffects;
    }

    @Override
    public Effects getEffects(Game game, EffectType effectType) {
        Effects typedEffects = new Effects();
        for (Effect effect : getEffects()) {
            if (effect.getEffectType() == effectType) {
                typedEffects.add(effect);
            }
        }
        return typedEffects;
    }

    @Override
    public Zone getZone() {
        return zone;
    }

    @Override
    public List<Watcher> getWatchers() {
        if (watchers != null) {
            return watchers;
        } else {
            return emptyWatchers;
        }
    }

    @Override
    public void addWatcher(Watcher watcher) {
        if (watchers == null) {
            watchers = new ArrayList<>();
        }
        watcher.setSourceId(this.sourceId);
        watcher.setControllerId(this.controllerId);
        watchers.add(watcher);
    }

    @Override
    public List<Ability> getSubAbilities() {
        if (subAbilities != null) {
            return subAbilities;
        } else {
            return emptyAbilities;
        }
    }

    @Override
    public void addSubAbility(Ability ability) {
        if (subAbilities == null) {
            subAbilities = new ArrayList<>();
        }
        ability.setSourceId(this.sourceId);
        ability.setControllerId(this.controllerId);
        subAbilities.add(ability);
    }

    @Override
    public boolean isUsesStack() {
        return usesStack;
    }

    @Override
    public String getRule() {
        return getRule(false);
    }

    @Override
    public String getRule(boolean all) {
        StringBuilder sbRule = threadLocalBuilder.get();
        if (all || this.abilityType != AbilityType.SPELL) {
            if (manaCosts.size() > 0) {
                sbRule.append(manaCosts.getText());
            }
            if (costs.size() > 0) {
                if (sbRule.length() > 0) {
                    sbRule.append(",");
                }
                sbRule.append(costs.getText());
            }
            if (sbRule.length() > 0) {
                sbRule.append(": ");
            }
        }

        String ruleStart = sbRule.toString();
        String text = getModes().getText();
        String rule;
        if (!text.isEmpty()) {
            if (ruleStart.length() > 1) {
                String end = ruleStart.substring(ruleStart.length() - 2).trim();
                if (end.isEmpty() || end.equals(":") || end.equals(".")) {
                    rule = ruleStart + Character.toUpperCase(text.charAt(0)) + text.substring(1);
                } else {
                    rule = ruleStart + text;
                }
            } else {
                rule = ruleStart + text;
            }
        } else {
            rule = ruleStart;
        }
        if (abilityWord != null) {
            rule = "<i>" + abilityWord + "</i> &mdash; " + Character.toUpperCase(rule.charAt(0)) + rule.substring(1);
        }
        return rule;
    }

    @Override
    public String getRule(String source) {
        return formatRule(getRule(), source);
    }

    protected String formatRule(String rule, String source) {
        String replace = rule;
        if (rule != null && source != null && !source.isEmpty()) {
            replace = rule.replace("{this}", source);
            replace = replace.replace("{source}", source);
        }
        return replace;
    }

    @Override
    public void addCost(Cost cost) {
        if (cost != null) {
            if (cost instanceof ManaCost) {
                this.addManaCost((ManaCost) cost);
            } else {
                this.costs.add(cost);
            }
        }
    }

    @Override
    public void addManaCost(ManaCost cost) {
        if (cost != null) {
            this.manaCosts.add(cost);
            this.manaCostsToPay.add(cost);
        }
    }

    @Override
    public void addOptionalCost(Cost cost) {
        if (cost != null) {
            this.optionalCosts.add(cost);
        }
    }

    @Override
    public void addEffect(Effect effect) {
        if (effect != null) {
            getEffects().add(effect);
        }
    }

    @Override
    public void addTarget(Target target) {
        if (target != null) {
            getTargets().add(target);
        }
    }

    @Override
    public Targets getTargets() {
        return getModes().getMode().getTargets();
    }

    @Override
    public UUID getFirstTarget() {
        return getTargets().getFirstTarget();
    }

    @Override
    public boolean isModal() {
        return getModes().size() > 1;
    }

    @Override
    public void addMode(Mode mode) {
        getModes().addMode(mode);
    }

    @Override
    public Modes getModes() {
        return modes;
    }

    @Override
    public boolean canChooseTarget(Game game) {
        int found = 0;
        for (Mode mode : getModes().values()) {
            if (mode.getTargets().canChoose(sourceId, controllerId, game)) {
                found++;
                if (getModes().isEachModeMoreThanOnce()) {
                    return true;
                }
                if (found >= getModes().getMinModes()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param game
     * @param source
     * @return
     */
    @Override
    public boolean isInUseableZone(Game game, MageObject source, GameEvent event) {
        if (!this.hasSourceObjectAbility(game, source, event)) {
            return false;
        }
        if (zone.equals(Zone.COMMAND)) {
            if (this.getSourceId() == null) { // commander effects
                return true;
            }
            MageObject object = game.getObject(this.getSourceId());
            // emblem are always actual
            if (object != null && object instanceof Emblem) {
                return true;
            }
        }

        UUID parameterSourceId;
        // for singleton abilities like Flying we can't rely on abilities' source because it's only once in continuous effects
        // so will use the sourceId of the object itself that came as a parameter if it is not null
        if (this instanceof MageSingleton && source != null) {
            parameterSourceId = source.getId();
        } else {
            parameterSourceId = getSourceId();
        }
        // check agains shortLKI for effects that move multiple object at the same time (e.g. destroy all)
        if (game.getShortLivingLKI(getSourceId(), getZone())) {
            return true;
        }
        // check against current state
        Zone test = game.getState().getZone(parameterSourceId);
        return test != null && zone.match(test);
    }

    @Override
    public boolean hasSourceObjectAbility(Game game, MageObject source, GameEvent event) {
        MageObject object = source;
        // for singleton abilities like Flying we can't rely on abilities' source because it's only once in continuous effects
        // so will use the sourceId of the object itself that came as a parameter if it is not null
        if (object == null) {
            object = game.getPermanentEntering(getSourceId());
            if (object == null) {
                object = game.getObject(getSourceId());
            }
        }
        if (object != null) {
            if (object instanceof Permanent) {
                if (!((Permanent) object).getAbilities(game).contains(this)) {
                    return false;
                }
                return ((Permanent) object).isPhasedIn();
            } else if (!object.getAbilities().contains(this)) {
                // check if it's an ability that is temporary gained to a card
                Abilities<Ability> otherAbilities = game.getState().getAllOtherAbilities(this.getSourceId());
                if (otherAbilities == null || !otherAbilities.contains(this)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return getRule();
    }

    @Override
    public boolean getRuleAtTheTop() {
        return ruleAtTheTop;
    }

    @Override
    public void setRuleAtTheTop(boolean ruleAtTheTop) {
        this.ruleAtTheTop = ruleAtTheTop;
    }

    @Override
    public boolean getRuleVisible() {
        return ruleVisible;
    }

    @Override
    public void setRuleVisible(boolean ruleVisible) {
        if (!(this instanceof MageSingleton)) { // prevent to change singletons
            this.ruleVisible = ruleVisible;
        }
    }

    @Override
    public boolean getAdditionalCostsRuleVisible() {
        return ruleAdditionalCostsVisible;
    }

    @Override
    public void setAdditionalCostsRuleVisible(boolean ruleAdditionalCostsVisible) {
        this.ruleAdditionalCostsVisible = ruleAdditionalCostsVisible;
    }

    @Override
    public UUID getOriginalId() {
        return this.originalId;
    }

    @Override
    public void setAbilityWord(AbilityWord abilityWord) {
        this.abilityWord = abilityWord;
    }

    @Override
    public String getGameLogMessage(Game game) {
        if (game.isSimulation()) {
            return "";
        }
        MageObject object = game.getObject(this.sourceId);
        if (object == null) { // e.g. sacrificed token
            logger.warn("Could get no object: " + this.toString());
        }
        return new StringBuilder(" activates: ")
                .append(object != null ? this.formatRule(getModes().getText(), object.getLogName()) : getModes().getText())
                .append(" from ")
                .append(getMessageText(game)).toString();
    }

    protected String getMessageText(Game game) {
        StringBuilder sb = threadLocalBuilder.get();
        MageObject object = game.getObject(this.sourceId);
        if (object != null) {
            if (object instanceof StackAbility) {
                Card card = game.getCard(((StackAbility) object).getSourceId());
                if (card != null) {
                    sb.append(GameLog.getColoredObjectIdName(card));
                } else {
                    sb.append(GameLog.getColoredObjectIdName(object));
                }
            } else if (object instanceof Spell) {
                Spell spell = (Spell) object;
                String castText = spell.getSpellCastText(game);
                sb.append((castText.startsWith("Cast ") ? castText.substring(5) : castText));
                if (spell.getFromZone() == Zone.GRAVEYARD) {
                    sb.append(" from graveyard");
                }
                sb.append(getOptionalTextSuffix(game, spell));
            } else {
                sb.append(GameLog.getColoredObjectIdName(object));
            }
        } else {
            sb.append("unknown");
        }
        if (object instanceof Spell && ((Spell) object).getSpellAbilities().size() > 1) {
            if (((Spell) object).getSpellAbility().getSpellAbilityType().equals(SpellAbilityType.SPLIT_FUSED)) {
                Spell spell = (Spell) object;
                int i = 0;
                for (SpellAbility spellAbility : spell.getSpellAbilities()) {
                    i++;
                    String half;
                    if (i == 1) {
                        half = " left";
                    } else {
                        half = " right";
                    }
                    if (spellAbility.getTargets().size() > 0) {
                        sb.append(half).append(" half targeting ");
                        for (Target target : spellAbility.getTargets()) {
                            sb.append(target.getTargetedName(game));
                        }
                    }
                }
            } else {
                Spell spell = (Spell) object;
                int i = 0;
                for (SpellAbility spellAbility : spell.getSpellAbilities()) {
                    i++;
                    if (i > 1) {
                        sb.append(" splicing ");
                        if (spellAbility.name.length() > 5 && spellAbility.name.startsWith("Cast ")) {
                            sb.append(spellAbility.name.substring(5));
                        } else {
                            sb.append(spellAbility.name);
                        }
                    }
                    sb.append(getTargetDescriptionForLog(spellAbility.getTargets(), game));
                }
            }
        } else if (object instanceof Spell && ((Spell) object).getSpellAbility().getModes().size() > 1) {
            Modes spellModes = ((Spell) object).getSpellAbility().getModes();
            for (UUID selectedModeId : spellModes.getSelectedModes()) {
                Mode selectedMode = spellModes.get(selectedModeId);
                int item = 0;
                for (Mode mode : spellModes.values()) {
                    item++;
                    if (mode.getId().equals(selectedMode.getId())) {
                        sb.append(" (mode ").append(item).append(")");
                        sb.append(getTargetDescriptionForLog(selectedMode.getTargets(), game));
                        break;
                    }
                }
            }
        } else {
            sb.append(getTargetDescriptionForLog(getTargets(), game));
        }
        return sb.toString();
    }

    @Override
    public String getTargetDescription(Targets targets, Game game) {
        return getTargetDescriptionForLog(targets, game);
    }

    protected String getTargetDescriptionForLog(Targets targets, Game game) {
        StringBuilder sb = new StringBuilder(); // threadLocal StringBuilder can't be used because calling method already uses it
        if (targets.size() > 0) {
            String usedVerb = null;
            for (Target target : targets) {
                if (!target.getTargets().isEmpty()) {
                    if (!target.isNotTarget()) {
                        if (usedVerb == null || usedVerb.equals(" choosing ")) {
                            usedVerb = " targeting ";
                            sb.append(usedVerb);
                        }
                    } else if (target.isNotTarget() && (usedVerb == null || usedVerb.equals(" targeting "))) {
                        usedVerb = " choosing ";
                        sb.append(usedVerb);
                    }
                    sb.append(target.getTargetedName(game));
                }
            }
        }
        return sb.toString();
    }

    private String getOptionalTextSuffix(Game game, Spell spell) {
        StringBuilder sb = new StringBuilder();
        for (Ability ability : spell.getAbilities()) {
            if (ability instanceof OptionalAdditionalSourceCosts) {
                sb.append(((OptionalAdditionalSourceCosts) ability).getCastMessageSuffix());
            }
            if (ability instanceof AlternativeSourceCosts && ((AlternativeSourceCosts) ability).isActivated(this, game)) {
                sb.append(((AlternativeSourceCosts) ability).getCastMessageSuffix(game));
            }
        }
        return sb.toString();
    }

    @Override
    public void setCostModificationActive(boolean active) {
        this.costModificationActive = active;
    }

    @Override
    public boolean getWorksFaceDown() {
        return worksFaceDown;
    }

    @Override
    public void setWorksFaceDown(boolean worksFaceDown) {
        this.worksFaceDown = worksFaceDown;
    }

    @Override
    public MageObject getSourceObject(Game game) {
        if (sourceObject == null) {
            setSourceObject(null, game);
            if (sourceObject == null) {
                logger.warn("Source object could not be retrieved: " + this.getRule());
            }
        }
        return sourceObject;
    }

    @Override
    public MageObject getSourceObjectIfItStillExists(Game game) {
        MageObject currentObject = game.getObject(getSourceId());
        if (currentObject != null) {
            if (sourceObject == null) {
                setSourceObject(currentObject, game);
            }
            MageObjectReference mor = new MageObjectReference(currentObject, game);
            if (mor.getZoneChangeCounter() == getSourceObjectZoneChangeCounter()) {
                // source object has meanwhile not changed zone
                return currentObject;
            }
        }
        return null;
    }

    @Override
    public int getSourceObjectZoneChangeCounter() {
        return sourceObjectZoneChangeCounter;
    }

    @Override
    public void setSourceObject(MageObject sourceObject, Game game) {
        if (sourceObject == null) {
            this.sourceObject = game.getObject(sourceId);
        } else {
            this.sourceObject = sourceObject;
        }
        this.sourceObjectZoneChangeCounter = game.getState().getZoneChangeCounter(sourceId);
    }

    @Override
    public boolean canFizzle() {
        return canFizzle;
    }

    @Override
    public void setCanFizzle(boolean canFizzle) {
        this.canFizzle = canFizzle;
    }

}
