package net.minecraft.scoreboard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.PersistentState;

public class ScoreboardState extends PersistentState {
	public static final String SCOREBOARD_KEY = "scoreboard";
	private final Scoreboard scoreboard;

	public ScoreboardState(Scoreboard scoreboard) {
		this.scoreboard = scoreboard;
	}

	public void unpack(ScoreboardState.Packed packed) {
		packed.objectives().forEach(this.scoreboard::addObjective);
		packed.scores().forEach(this.scoreboard::addEntry);
		packed.displaySlots().forEach((slot, objectiveName) -> {
			ScoreboardObjective scoreboardObjective = this.scoreboard.getNullableObjective(objectiveName);
			this.scoreboard.setObjectiveSlot(slot, scoreboardObjective);
		});
		packed.teams().forEach(this.scoreboard::addTeam);
	}

	public ScoreboardState.Packed pack() {
		Map<ScoreboardDisplaySlot, String> map = new EnumMap(ScoreboardDisplaySlot.class);

		for (ScoreboardDisplaySlot scoreboardDisplaySlot : ScoreboardDisplaySlot.values()) {
			ScoreboardObjective scoreboardObjective = this.scoreboard.getObjectiveForSlot(scoreboardDisplaySlot);
			if (scoreboardObjective != null) {
				map.put(scoreboardDisplaySlot, scoreboardObjective.getName());
			}
		}

		return new ScoreboardState.Packed(
			this.scoreboard.getObjectives().stream().map(ScoreboardObjective::pack).toList(),
			this.scoreboard.pack(),
			map,
			this.scoreboard.getTeams().stream().map(Team::pack).toList()
		);
	}

	public record Packed(
		List<ScoreboardObjective.Packed> objectives, List<Scoreboard.PackedEntry> scores, Map<ScoreboardDisplaySlot, String> displaySlots, List<Team.Packed> teams
	) {
		public static final Codec<ScoreboardState.Packed> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ScoreboardObjective.Packed.CODEC.listOf().optionalFieldOf("Objectives", List.of()).forGetter(ScoreboardState.Packed::objectives),
					Scoreboard.PackedEntry.CODEC.listOf().optionalFieldOf("PlayerScores", List.of()).forGetter(ScoreboardState.Packed::scores),
					Codec.unboundedMap(ScoreboardDisplaySlot.CODEC, Codec.STRING).optionalFieldOf("DisplaySlots", Map.of()).forGetter(ScoreboardState.Packed::displaySlots),
					Team.Packed.CODEC.listOf().optionalFieldOf("Teams", List.of()).forGetter(ScoreboardState.Packed::teams)
				)
				.apply(instance, ScoreboardState.Packed::new)
		);
	}
}
