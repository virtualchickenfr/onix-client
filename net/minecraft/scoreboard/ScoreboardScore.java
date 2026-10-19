package net.minecraft.scoreboard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.NumberFormatTypes;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import org.jetbrains.annotations.Nullable;

public class ScoreboardScore implements ReadableScoreboardScore {
	public static final MapCodec<ScoreboardScore> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.INT.optionalFieldOf("Score", 0).forGetter(ScoreboardScore::getScore),
				Codec.BOOL.optionalFieldOf("Locked", false).forGetter(ScoreboardScore::isLocked),
				TextCodecs.CODEC.optionalFieldOf("display").forGetter(score -> Optional.ofNullable(score.displayText)),
				NumberFormatTypes.CODEC.optionalFieldOf("format").forGetter(score -> Optional.ofNullable(score.numberFormat))
			)
			.apply(instance, ScoreboardScore::new)
	);
	private int score;
	private boolean locked = true;
	@Nullable
	private Text displayText;
	@Nullable
	private NumberFormat numberFormat;

	public ScoreboardScore() {
	}

	private ScoreboardScore(int score, boolean locked, Optional<Text> displayText, Optional<NumberFormat> numberFormat) {
		this.score = score;
		this.locked = locked;
		this.displayText = (Text)displayText.orElse(null);
		this.numberFormat = (NumberFormat)numberFormat.orElse(null);
	}

	@Override
	public int getScore() {
		return this.score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Nullable
	public Text getDisplayText() {
		return this.displayText;
	}

	public void setDisplayText(@Nullable Text text) {
		this.displayText = text;
	}

	@Nullable
	@Override
	public NumberFormat getNumberFormat() {
		return this.numberFormat;
	}

	public void setNumberFormat(@Nullable NumberFormat numberFormat) {
		this.numberFormat = numberFormat;
	}
}
