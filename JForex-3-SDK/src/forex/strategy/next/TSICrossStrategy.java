package forex.strategy.next;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class TSICrossStrategy implements IStrategy {

	private final String id = generateStrategyId(this.getClass().getSimpleName().toUpperCase());

	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private int counter = 0;
	private static final int MAX_ID_LENGTH = 5;

	private static Set<IOrder> trailedOrders = new HashSet<>();
	IOrder lastOrder = null;

	private IConsole console;

	@Configurable("Instrument")
	public Instrument instrument = Instrument.EURUSD;// Instrument.EURUSD;
	@Configurable("Period")
	public Period selectedPeriod = Period.FIFTEEN_MINS; // - Period.FOUR_HOURS;
	@Configurable("SMA filter")
	private Filter indicatorFilter = Filter.WEEKENDS;
	@Configurable("Amount")
	public double defaultTradeAmount = 0.1;
	@Configurable("slippage:")
	public int slippage = 0;

	private static final double SAR_MAX = 0.2;
	private static final double SAR_ACCELERATION = 0.02;

	@Configurable("Stop loss")
	public int stopLossPips = 40;
	@Configurable("Take profit")
	public int takeProfitPips = 90;
	@Configurable("Trailing Step")
	public double trailStep = 10;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.console = context.getConsole();
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		printOnMessage(console, message, id);
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

		if (!instrument.equals(instrument) || !period.equals(selectedPeriod) || dontTradePeriod(askBar))
			return;

		final IBar prevBar = history.getBar(instrument, selectedPeriod, OfferSide.BID, 2);

		double adxBuy = indicators.adx(instrument, selectedPeriod, OfferSide.BID, 14, 1);
		// boolean isSMABuy = isSMACrossBuyNoFeed(indicators, instrument, period,
		// OfferSide.BID, bidBar.getTime());
		boolean isSARBuy = isSARBuy(bidBar, prevBar);
		boolean isTSIBuy = isTSIBuy(bidBar);

		if (isSARBuy && isTSIBuy && adxBuy >= 25) {
			submitOrder(OrderCommand.BUY, bidBar, askBar);
		}

		double adxSell = indicators.adx(instrument, selectedPeriod, OfferSide.ASK, 14, 1);
		// boolean isSMASell = isSMACrossSellNoFeed(indicators, instrument, period,
		// OfferSide.ASK,askBar.getTime());
		boolean isSARSell = isSARSell(askBar, prevBar);
		boolean isTSISell = isTSISell(askBar);

		if (isSARSell && isTSISell && adxSell >= 25) {
			submitOrder(OrderCommand.SELL, bidBar, askBar);
		}
	}

	private boolean isTSIBuy(final IBar bidBar) throws JFException {
		// TSI - Signal - Histo
		final Object[] tsiInd = indicators.calculateIndicator(instrument, selectedPeriod,
				new OfferSide[] { OfferSide.BID }, "TSI", new IIndicators.AppliedPrice[] { AppliedPrice.CLOSE },
				new Object[] { 40, 20, 10 }, indicatorFilter, 2, bidBar.getTime(), 0);

		final double[] tsi = (double[]) tsiInd[0];
		final double[] tsiSignal = (double[]) tsiInd[1];

		final double tsiNow = (double) tsi[1];
		final double tsiPrev = (double) tsi[0];

		final double tsiSignalNow = (double) tsiSignal[1];
		final double tsiSignalPrev = (double) tsiSignal[0];

		return (tsiNow < 0 && tsiSignalNow < 0) && (tsiPrev <= tsiSignalPrev && tsiNow > tsiSignalNow);
	}

	private boolean isTSISell(final IBar askBar) throws JFException {
		// TSI - Signal - Histo
		final Object[] tsiInd = indicators.calculateIndicator(instrument, selectedPeriod,
				new OfferSide[] { OfferSide.ASK }, "TSI", new IIndicators.AppliedPrice[] { AppliedPrice.CLOSE },
				new Object[] { 40, 20, 10 }, indicatorFilter, 2, askBar.getTime(), 0);

		final double[] tsi = (double[]) tsiInd[0];
		final double[] tsiSignal = (double[]) tsiInd[1];

		final double tsiNow = (double) tsi[1];
		final double tsiPrev = (double) tsi[0];

		final double tsiSignalNow = (double) tsiSignal[1];
		final double tsiSignalPrev = (double) tsiSignal[0];

		return (tsiNow > 0 && tsiSignalNow > 0) && (tsiPrev >= tsiSignalPrev && tsiNow < tsiSignalNow);
	}

	private boolean isSARBuy(final IBar bidBar, final IBar prevBar) throws JFException {
		double yesterdaySAR = indicators.sar(instrument, selectedPeriod, OfferSide.BID, SAR_ACCELERATION, SAR_MAX, 2);
		double todaySAR = indicators.sar(instrument, selectedPeriod, OfferSide.BID, SAR_ACCELERATION, SAR_MAX, 1);

		return prevBar.getHigh() < yesterdaySAR && bidBar.getHigh() > todaySAR;
	}

	private boolean isSARSell(final IBar askBar, final IBar prevBar) throws JFException {
		double yesterdaySAR = indicators.sar(instrument, selectedPeriod, OfferSide.ASK, SAR_ACCELERATION, SAR_MAX, 2);
		double todaySAR = indicators.sar(instrument, selectedPeriod, OfferSide.ASK, SAR_ACCELERATION, SAR_MAX, 1);

		return prevBar.getLow() > yesterdaySAR && askBar.getLow() < todaySAR;
	}

	public boolean isSMACrossBuyNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide, long time) throws JFException {

		final int CURRENT = 1;
		final int PREVIOUS = 0;
		double[] shortMA = indicators.ma(instrument, this.selectedPeriod, offerSide, AppliedPrice.CLOSE, 5, MaType.SMA,
				indicatorFilter, 2, time, 0);
		double[] longMA = indicators.ma(instrument, this.selectedPeriod, offerSide, AppliedPrice.CLOSE, 30, MaType.SMA,
				indicatorFilter, 2, time, 0);

		return shortMA[PREVIOUS] <= longMA[PREVIOUS] && shortMA[CURRENT] > longMA[CURRENT];
	}

	public boolean isSMACrossSellNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide, long time) throws JFException {

		final int CURRENT = 1;
		final int PREVIOUS = 0;
		double[] shortMA = indicators.ma(instrument, this.selectedPeriod, offerSide, AppliedPrice.CLOSE, 5, MaType.SMA,
				indicatorFilter, 2, time, 0);
		double[] longMA = indicators.ma(instrument, this.selectedPeriod, offerSide, AppliedPrice.CLOSE, 30, MaType.SMA,
				indicatorFilter, 2, time, 0);

		return shortMA[PREVIOUS] >= longMA[PREVIOUS] && shortMA[CURRENT] < longMA[CURRENT];
	}

	private IOrder submitOrder(OrderCommand orderCmd, final IBar bidBar, final IBar askBar) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		if (orderCmd == OrderCommand.BUY) {
			final double atr = indicators.atr(instrument, Period.FOUR_HOURS, OfferSide.BID, 21, 1);
			double lastTickBid = history.getLastTick(this.instrument).getBid();
			// takeProfitPrice = roundDouble((atr * 4) + lastTickBid, 5);
			// stopLossPrice = roundDouble(lastTickBid - (atr * 1.5), 5);

			takeProfitPrice = lastTickBid + getPipPrice(this.takeProfitPips);
			stopLossPrice = lastTickBid - getPipPrice(this.stopLossPips);

			System.err.println("--> BUYING [" + history.getLastTick(this.instrument).getBid() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		} else {
			final double atr = indicators.atr(instrument, Period.FOUR_HOURS, OfferSide.ASK, 21, 1);
			double lastTickAsk = history.getLastTick(this.instrument).getAsk();
			// takeProfitPrice = roundDouble(lastTickAsk - (atr * 4), 5);
			// stopLossPrice = roundDouble((atr * 1.5) + lastTickAsk, 5);

			takeProfitPrice = lastTickAsk - getPipPrice(this.takeProfitPips);
			stopLossPrice = lastTickAsk + getPipPrice(this.stopLossPips);

			System.err.println("--> SELLING [" + history.getLastTick(this.instrument).getAsk() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		}

		// double newamount = calculateAmountFromEquity();
		double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
		final String orderLabel = getLabel(instrument, id, ++counter);

		return engine.submitOrder(orderLabel, this.instrument, orderCmd, newamount, 0, slippage, stopLossPrice,
				takeProfitPrice);
	}

	private double getPipPrice(int pips) {
		return pips * this.instrument.getPipValue();
	}

	public static String getLabel(final Instrument instrument, final String id, final int counter) {
		return id + instrument.name().substring(0, 2) + instrument.name().substring(3, 5)
				+ String.format("%8d", counter).replace(" ", "0");
	}

	public static void printOnMessage(final IConsole console, final IMessage message, final String id)
			throws JFException {
		// Print messages, but related to own orders
		if (message.getOrder() != null && message.getOrder().getLabel().substring(0, MAX_ID_LENGTH).equals(id)) {
			String orderLabel = message.getOrder().getLabel();
			IMessage.Type messageType = message.getType();
			switch (messageType) {
			// Ignore the following
			case ORDER_FILL_OK:
			case ORDER_CHANGED_OK:
				break;
			case ORDER_SUBMIT_OK:
				break;
			case ORDER_CLOSE_OK:
				console.getErr()
						.println(orderLabel + " : " + message.getOrder().getOrderCommand().toString() + " : "
								+ messageType + ": " + "Pips: " + message.getOrder().getProfitLossInPips() + " - $$$: "
								+ message.getOrder().getProfitLossInAccountCurrency() + "$");
				break;
			case ORDERS_MERGE_OK:
				console.getInfo().println(orderLabel + " " + messageType);
				break;
			case NOTIFICATION:
				console.getNotif().println(orderLabel + " " + message.getContent().replaceAll(".*-Order", "Order"));
				break;
			case ORDER_CHANGED_REJECTED:
			case ORDER_CLOSE_REJECTED:
			case ORDER_FILL_REJECTED:
			case ORDER_SUBMIT_REJECTED:
			case ORDERS_MERGE_REJECTED: {
				console.getWarn().println(orderLabel + " " + messageType + " - size:" + message.getReasons().size()
						+ " - cont:" + message.getContent());
			}
				break;
			default:
				console.getErr().println(orderLabel + " *" + messageType + "* " + message.getContent());
				break;
			}
		}
	}

	public static String generateStrategyId(final String className) {
		return className.substring(0, MAX_ID_LENGTH);
	}

	private double roundDouble(double num, int dec) {
		return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
		for (IOrder order : engine.getOrders()) {
			if (order.getState() == IOrder.State.FILLED || order.getState() == IOrder.State.OPENED) {

				if ((order.getProfitLossInPips() / takeProfitPips >= 0.8) && !trailedOrders.contains(order)) {
					try {
						OfferSide side = order.isLong() ? OfferSide.BID : OfferSide.ASK;

						// double profitPrice = order.isLong() ? tick.getBid() + getPipPrice(40)
						// : tick.getAsk() - getPipPrice(40);
						// double stopPrice = order.isLong() ? tick.getBid() - getPipPrice(10)
						// : tick.getAsk() + getPipPrice(10);

						double stopPrice = order.isLong() ? order.getOpenPrice() + getPipPrice(10)
								: order.getOpenPrice() - getPipPrice(10);

						// order.setTakeProfitPrice(profitPrice);
						// order.waitForUpdate(1000);
						order.setStopLossPrice(stopPrice, side, trailStep);

						trailedOrders.add(order);
						System.err.println("--- TRAILING[" + order.getLabel() + "]: PriceNow:" + tick.getAsk()
								+ " - take: " + "profitPrice" + " - stopPrice:" + stopPrice);
					} catch (JFException ex) {
						console.getErr().println(order.getLabel() + "-- couldn't set newStop: "
								+ order.getStopLossPrice() + ", trailStep: " + trailStep);
					}
				}
			}
		}
	}

	private boolean dontTradePeriod(IBar askBar) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(new Date(askBar.getTime()));

		return (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && cal.get(Calendar.HOUR_OF_DAY) >= 18)
				|| (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
				|| (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) || (cal.get(Calendar.MONTH) == Calendar.JULY)
				|| (cal.get(Calendar.MONTH) == Calendar.AUGUST);
	}
}
