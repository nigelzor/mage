/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mage.abilities.common;

import mage.abilities.effects.Effect;
import mage.constants.PhaseStep;
import mage.game.Game;
import mage.game.events.GameEvent;

/**
 *
 * @author LevelX2
 */
public class DealsDamageToOneOrMoreCreaturesTriggeredAbility extends DealsDamageToACreatureTriggeredAbility {

    public DealsDamageToOneOrMoreCreaturesTriggeredAbility(Effect effect, boolean combatOnly, boolean optional, boolean setTargetPointer) {
        super(effect, combatOnly, optional, setTargetPointer);
    }

    public DealsDamageToOneOrMoreCreaturesTriggeredAbility(DealsDamageToOneOrMoreCreaturesTriggeredAbility ability) {
        super(ability);
    }

    @Override
    public boolean checkTrigger(GameEvent event, Game game) {
        if (super.checkTrigger(event, game)) {
            // check that combat damage does only once trigger also if multiple creatures were damaged because they block or were blocked by source
            if (game.getTurn().getStepType().equals(PhaseStep.COMBAT_DAMAGE) || game.getTurn().getStepType().equals(PhaseStep.FIRST_COMBAT_DAMAGE)) {
                Integer stepHash = (Integer) game.getState().getValue("damageStep" + getOriginalId());
                if (stepHash == null || game.getStep().hashCode() != stepHash) {
                    // this ability did not trigger during this damage step
                    game.getState().setValue("damageStep" + getOriginalId(), game.getStep().hashCode());
                    return true;
                }
            } else {
                game.getState().setValue("damageStep" + getOriginalId(), null);
                return true;
            }
            // TODO: check that if the source did non combat damage to multiple targets at the same time, it may only trigger one time
            // I don't know currently how this can happen for a source creature that this has not already build in
        }
        return false;
    }

    @Override
    public DealsDamageToOneOrMoreCreaturesTriggeredAbility copy() {
        return new DealsDamageToOneOrMoreCreaturesTriggeredAbility(this);
    }

}
