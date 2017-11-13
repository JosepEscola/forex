package forex.strategy.calculators;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.indicators.IIndicatorCalculator;

public final class SMACalculator {

	/**
	 * 
	 * @param history
	 * @param indicatorFilter
	 * @param instrument
	 * @param indicators
	 * @param period
	 * @return
	 * @throws JFException
	 */
	public static boolean isSMACrossBuy(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		// final IBar prevBar = history.getBar(feedDescriptor.getInstrument(),
		// feedDescriptor.getPeriod(), OfferSide.BID,
		// 1);
		// double sma = calculateSMA(history, feedDescriptor, indicators);

		int candlesBefore = 1;
		int candlesAfter = 0;

		// ITimedData feedData = history.getFeedData(feedDescriptor, 0);

		final IBar prevBar = history.getBar(feedDescriptor.getInstrument(), feedDescriptor.getPeriod(), OfferSide.BID,
				1);

		IIndicatorCalculator<Double, double[]> fullSMA = indicators.sma(feedDescriptor, AppliedPrice.CLOSE,
				feedDescriptor.getOfferSide(), 10);

		// 10, 20, 50, 100 , 200
		double[] sma10 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 10)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma20 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 20)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma50 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 50)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma100 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 100)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma200 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 200)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);

		if ((sma10[0] > sma20[0]) && (sma20[0] > sma50[0]) && (sma50[0] > sma100[0]) && (sma100[0] > sma200[0])) {
			// OK
			return true;
		}

		/**
		 * In uptrend, SMAs with shorter time periods must be running above SMAs with
		 * longer time periods.
		 */
		// TODO: AND [Daily SMA(20,Daily Volume) > 40000]
		// TODO: AND [Daily SMA(60,Daily Close) > 10]

		// SMA crossed previous green candle
		// if (prevBar.getOpen() < sma && prevBar.getClose() > sma) {
		// System.err.println("SMAAA IN --> BUY");
		// // submitOrder(OrderCommand.BUY);
		// return true;
		// }

		return false;
	}

	public static boolean isSMACrossSell(IHistory history, IFeedDescriptor feedDescriptor, IIndicators indicators)
			throws JFException {

		// final IBar prevBar = history.getBar(feedDescriptor.getInstrument(),
		// feedDescriptor.getPeriod(), OfferSide.BID,
		// 1);
		// double sma = calculateSMA(history, feedDescriptor, indicators);

		int candlesBefore = 1;
		int candlesAfter = 0;

		// ITimedData feedData = history.getFeedData(feedDescriptor, 0);

		final IBar prevBar = history.getBar(feedDescriptor.getInstrument(), feedDescriptor.getPeriod(), OfferSide.BID,
				1);

		IIndicatorCalculator<Double, double[]> fullSMA = indicators.sma(feedDescriptor, AppliedPrice.CLOSE,
				feedDescriptor.getOfferSide(), 10);

		// 10, 20, 50, 100 , 200
		double[] sma10 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 10)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma20 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 20)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma50 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 50)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma100 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 100)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma200 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 200)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);

		if ((sma10[0] < sma20[0]) && (sma20[0] < sma50[0]) && (sma50[0] < sma100[0]) && (sma100[0] < sma200[0])) {
			// OK
			return true;
		}
		/**
		 * In downtrend, SMAs with shorter time periods must be running below SMAs with
		 * longer time periods
		 */
		// SMA crossed previous green candle
		// if (prevBar.getOpen() < sma && prevBar.getClose() > sma) {
		// System.err.println("SMAAA IN --> BUY");
		// // submitOrder(OrderCommand.BUY);
		// return true;
		// }
		// // SMA crossed previous red candle
		// if (prevBar.getOpen() > sma && prevBar.getClose() < sma) {
		// System.err.println("SAR IN --> SELL");
		// // submitOrder(OrderCommand.SELL);
		// return true;
		// }
		return false;
	}

	/**
	 * 
	 * @param indicatorFilter
	 * @param instrument
	 * @param indicators
	 * @param period
	 *            of the Bar to calculate
	 * @param prevBar
	 * @return
	 * @throws JFException
	 */
	private static double calculateSMA(final IHistory history, final IFeedDescriptor feedDescriptor,
			final IIndicators indicators) throws JFException {

		// int smaTimePeriod = 50;
		int candlesBefore = 1;
		int candlesAfter = 0;

		// ITimedData feedData = history.getFeedData(feedDescriptor, 0);

		final IBar prevBar = history.getBar(feedDescriptor.getInstrument(), feedDescriptor.getPeriod(), OfferSide.BID,
				1);

		IIndicatorCalculator<Double, double[]> fullSMA = indicators.sma(feedDescriptor, AppliedPrice.CLOSE,
				feedDescriptor.getOfferSide(), 10);

		// 10, 20, 50, 100 , 200
		double[] sma10 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 10)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma20 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 20)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma50 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 50)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma100 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 100)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);
		double[] sma200 = indicators.sma(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 200)
				.calculate(candlesBefore, prevBar.getTime(), candlesAfter);

		if ((sma10[0] > sma20[0]) && (sma20[0] > sma50[0]) && (sma50[0] > sma100[0]) && (sma100[0] > sma200[0])) {
			// OK
		}
		// System.err.println("sma10[]: " + Arrays.toString(sma10));
		// System.err.println("sma10[0]: " + sma10[0]);
		// double sma = indicators.sma(instrument, period, OfferSide.BID,
		// AppliedPrice.CLOSE, smaTimePeriod,
		// indicatorFilter, candlesBefore, prevBar.getTime(), candlesAfter)[0];

		return -1;
	}

	public static boolean isSMACrossBuy() {
		// TODO Auto-generated method stub
		return false;
	}
}
