package forex.strategy.next;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.IUserInterface;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class BollingerBandExample implements IStrategy {
	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IContext context;
	private IIndicators indicators;
	private IUserInterface userInterface;

	@Configurable("selectedInstrument:")
	public Instrument selectedInstrument = Instrument.EURUSD;
	@Configurable("selectedPeriod:")
	public Period selectedPeriod = Period.TEN_MINS;
	@Configurable("stopLossPips:")
	public int stopLossPips = 25;
	@Configurable("takeProfitPips:")
	public int takeProfitPips = 50;

	private boolean lowCross = false;
	private boolean upperCross = false;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.context = context;
		this.indicators = context.getIndicators();
		this.userInterface = context.getUserInterface();

		context.setSubscribedInstruments(Collections.singleton(selectedInstrument), true);
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
	}

	public void onStop() throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!instrument.equals(selectedInstrument) || !period.equals(selectedPeriod)) {
			return;
		}

		long time = bidBar.getTime();
		Object[] indicatorResult = indicators.calculateIndicator(selectedInstrument, selectedPeriod,
				new OfferSide[] { OfferSide.BID }, "BBANDS",
				new IIndicators.AppliedPrice[] { IIndicators.AppliedPrice.CLOSE }, new Object[] { 20, 2.0, 2.0, 0 },
				Filter.WEEKENDS, 1, time, 0);
		double bollingerUpperValue = ((double[]) indicatorResult[0])[0];
		double bollingerMiddleValue = ((double[]) indicatorResult[1])[0];
		double bollingerLowerValue = ((double[]) indicatorResult[2])[0];

		if (askBar.getClose() < bollingerLowerValue) {
			lowCross = true;
		}
		if (bidBar.getClose() > bollingerUpperValue) {
			upperCross = true;
		}

		if (lowCross && askBar.getClose() >= bollingerLowerValue) {
			boolean existLong = false;
			for (IOrder order : engine.getOrders(selectedInstrument)) {
				if (order.getState() == IOrder.State.OPENED || order.getState() == IOrder.State.FILLED) {
					existLong = order.isLong();
					if (existLong) {
						break;
					}
				}
			}

			if (!existLong) {
				double stopLoss = bidBar.getClose() - selectedInstrument.getPipValue() * stopLossPips;
				double takeProfit = bidBar.getClose() + selectedInstrument.getPipValue() * takeProfitPips;

				engine.submitOrder(getLabel(time), selectedInstrument, IEngine.OrderCommand.BUY, 0.1, 0, 5, stopLoss,
						takeProfit, 0, "");
			}

			upperCross = false;
			lowCross = false;

		} else if (upperCross && bidBar.getClose() <= bollingerUpperValue) {
			boolean existShort = false;
			for (IOrder order : engine.getOrders(selectedInstrument)) {
				if (order.getState() == IOrder.State.OPENED || order.getState() == IOrder.State.FILLED) {
					existShort = !order.isLong();
					if (existShort) {
						break;
					}
				}
			}

			if (!existShort) {
				double stopLoss = askBar.getClose() + selectedInstrument.getPipValue() * stopLossPips;
				double takeProfit = askBar.getClose() - selectedInstrument.getPipValue() * takeProfitPips;

				engine.submitOrder(getLabel(time), selectedInstrument, IEngine.OrderCommand.SELL, 0.1, 0, 5, stopLoss,
						takeProfit, 0, "");
			}

			upperCross = false;
			lowCross = false;
		}
	}

	private String getLabel(long time) {
		return "IVF" + DATE_FORMAT.format(time) + generateRandom(10000) + generateRandom(10000);
	}

	private String generateRandom(int n) {
		int randomNumber = (int) (Math.random() * n);
		String answer = "" + randomNumber;
		if (answer.length() > 3) {
			answer = answer.substring(0, 4);
		}
		return answer;
	}
}
