package forex.strategy.calculators;

import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.JFException;
import com.dukascopy.api.feed.IFeedDescriptor;

public final class CCICalculator {

	public static boolean isCCIBuy(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		// – Commodity Channel Index, Time Period – 14.

		double cci = indicators.cci(feedDescriptor, feedDescriptor.getOfferSide(), 14).calculate(1);
		return cci < -100;
	}

	public static boolean isCCISell(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		double cci = indicators.cci(feedDescriptor, feedDescriptor.getOfferSide(), 14).calculate(1);
		return cci > 100;
	}
}
