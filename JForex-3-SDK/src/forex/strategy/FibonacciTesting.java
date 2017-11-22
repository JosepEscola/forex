package forex.strategy;

import java.util.Arrays;
import java.util.List;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;

import forex.strategy.services.OnMessageService;
import forex.strategy.services.SubmitOrderService;

public class FibonacciTesting implements IStrategy {

	private final String id = OnMessageService.generateStrategyId(this.getClass().getSimpleName().toUpperCase());

	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private int counter = 0;

	private IConsole console;

	@Configurable("Instrument")
	private Instrument instrument = Instrument.EURUSD;// Instrument.EURUSD;
	@Configurable("Period")
	private Period selectedPeriod = Period.DAILY;// FIFTEEN_MINS;
	@Configurable("SMA filter")
	private Filter indicatorFilter = Filter.WEEKENDS;
	@Configurable("Amount")
	private double defaultTradeAmount = 5.0;// 0.1;

	// @Configurable("Stop loss")
	// private int stopLossPips = 25;// 10;
	// @Configurable("Take profit")
	// private int takeProfitPips = 75;// 90;

	/**
	 * Mes conservador
	 */
	@Configurable("Stop loss")
	private int stopLossPips = 50;
	@Configurable("Take profit")
	private int takeProfitPips = 30;

	private IFeedDescriptor feedDescriptor;

	private double lastEquity = 0;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.lastEquity = history.getEquity();
		this.console = context.getConsole();
		this.feedDescriptor = new TimePeriodAggregationFeedDescriptor(instrument, selectedPeriod, OfferSide.ASK,
				indicatorFilter);

	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		OnMessageService.printOnMessage(console, message, id);
	}

	public void onStop() throws JFException {
		double closePL = 0;
		// close all orders
		for (IOrder order : engine.getOrders()) {
			IOrder orderToClose = engine.getOrder(order.getLabel());
			orderToClose.close();
			closePL += orderToClose.getProfitLossInAccountCurrency();
		}
		System.err.println("TOTAL Closed Order: " + closePL + "$");
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {

		if (!this.instrument.equals(instrument) || !feedDescriptor.getPeriod().equals(period))
			return;

		long lastTickTime = history.getLastTick(instrument).getTime();
		long lastBarTime = history.getBarStart(period, lastTickTime);
		// List<IBar> bars = history.getBars(instrument, period, OfferSide.BID,
		// Filter.NO_FILTER, 10, lastBarTime, 0);

		List<IBar> bars = history.getBars(instrument, period, feedDescriptor.getOfferSide(), indicatorFilter, 20,
				lastBarTime, 0);

		double maxHigh = Double.MIN_VALUE;
		double minLow = Double.MAX_VALUE;
		double maxTime = 0;
		double minTime = 0;

		IBar maxBar = null;
		IBar minBar = null;
		for (IBar bar : bars) {
			if (maxHigh < bar.getHigh()) {
				maxHigh = bar.getHigh();
				maxBar = bar;
			}
			if (minLow > bar.getLow()) {
				minLow = bar.getLow();
				minBar = bar;
			}
			// console.getOut().println(bar);
		}

		if (maxBar.getHigh() != maxHigh) {
			System.err.println("HIGHS NOT EQULA!!!!");
		}
		if (minBar.getLow() != minLow) {
			System.err.println("LOWS NOT EQULA!!!!");
		}
		console.getOut()
				.println(String.format(instrument.toString() + ": over last %s bars of period[%s]- high=%.5f, low=%.5f",
						bars.size(), period.toString(), maxHigh, minLow));

		final boolean isBullish = minBar.getTime() < maxBar.getTime();
		// if (minBar.getTime() < maxBar.getTime()) {
		// // ASCENDING BULLISH
		//
		// } else if (minBar.getTime() > maxBar.getTime()) {
		// // DESCDENIG BEARISH
		//
		// }

		/**
		 * indicators.atr(feedDescriptor, side, 14);
		 **/

		double[][] fibPivot = indicators.fibPivot2(instrument, period, feedDescriptor.getOfferSide(), minBar.getTime(),
				maxBar.getTime());

		System.err.println(Arrays.deepToString(fibPivot));

		//
		// System.err.println("END");

	}

	/**
	 * If equity is lower than or equal to lastEquity, it calculates the amount
	 * according to the following formula: amount = min(Equity/20000.0,
	 * defaultTradeAmount). But if equity is greater than lastEquity it calculates
	 * the amount according to the following formula: amount = amount +
	 * defaultTradeAmount. 2. Updates lastEquity to the value of current equity
	 */
	private double calculateAmountFromEquity() {
		double newamount = defaultTradeAmount;

		if (history.getEquity() <= lastEquity) {
			// amount = min(Equity/20000.0,* defaultTradeAmount)
			newamount = Math.min(history.getEquity() / 20000.0, defaultTradeAmount);
		} else {
			newamount = newamount + defaultTradeAmount;
		}
		lastEquity = history.getEquity();
		return newamount;
	}

	/**
	 * 
	 * @param orderCmd
	 * @return
	 * @throws JFException
	 */
	private IOrder submitOrder(OrderCommand orderCmd) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		if (orderCmd == OrderCommand.BUY) {
			stopLossPrice = history.getLastTick(this.instrument).getBid() - getPipPrice(this.stopLossPips);
			takeProfitPrice = history.getLastTick(this.instrument).getBid() + getPipPrice(this.takeProfitPips);
			System.err.println("--> BUYING [" + history.getLastTick(this.instrument).getBid() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		} else {
			stopLossPrice = history.getLastTick(this.instrument).getAsk() + getPipPrice(this.stopLossPips);
			takeProfitPrice = history.getLastTick(this.instrument).getAsk() - getPipPrice(this.takeProfitPips);
			System.err.println("--> SELLING [" + history.getLastTick(this.instrument).getAsk() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		}

		// double newamount = calculateAmountFromEquity();
		double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
		final String orderLabel = SubmitOrderService.getLabel(instrument, id, ++counter);

		return engine.submitOrder(orderLabel, this.instrument, orderCmd, newamount, 0, 20, stopLossPrice,
				takeProfitPrice);
	}

	private double getPipPrice(int pips) {
		return pips * this.instrument.getPipValue();
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

}
