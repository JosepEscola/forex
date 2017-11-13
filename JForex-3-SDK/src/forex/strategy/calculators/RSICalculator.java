package forex.strategy.calculators;

import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.JFException;
import com.dukascopy.api.feed.IFeedDescriptor;

public final class RSICalculator {

	public static boolean isRSIBuy(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		// RSI(14) below 50 level.
		final double rsi = indicators.rsi(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 14)
				.calculate(1);
		return rsi < 50;
	}

	public static boolean isRSISell(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		final double rsi = indicators.rsi(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 14)
				.calculate(1);
		return rsi > 50;
	}
}
