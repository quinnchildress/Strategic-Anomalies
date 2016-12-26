package game.unit.ability;

import java.util.List;

import game.board.Square;
import game.interaction.incident.IncidentReporter;
import game.unit.Unit;

public abstract class ActiveAbilityProperty extends AbilityProperty {

    private final IncidentReporter onAbilityUseReporter;

    public ActiveAbilityProperty(Unit unitOwner, int initialPower, int initialAttackRange) {
	super(unitOwner, initialPower, initialAttackRange);

	onAbilityUseReporter = new IncidentReporter();
    }

    public IncidentReporter getOnUseReporter() {
	return onAbilityUseReporter;
    }

    public abstract List<Square> getAOESqaures(Square target);

    public final void useAbility(Square target) {
	performAbility(target);
	onAbilityUseReporter.reportIncident(target);
    }

    public abstract void performAbility(Square target);

}