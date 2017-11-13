package singlejartest;


import java.util.*;
import java.text.*;

import com.dukascopy.api.*;

public class SMASmallExample2 implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;

    @Configurable("selectedInstrument:")
    public Instrument selectedInstrument = Instrument.DAIDEEUR;
    @Configurable("tradeAmount:")
    public double tradeAmount = 0.0010;
    @Configurable("slippage:")
    public int slippage = 5;
    @Configurable("stopLossPips:")
    public int stopLossPips = 25;
    @Configurable("takeProfitPips:")
    public int takeProfitPips = 50;

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
        if (!instrument.equals(selectedInstrument)) {
            return;
        }

        if (!engine.getOrders(selectedInstrument).isEmpty()) {
            return;
        }

        long time = history.getBar(selectedInstrument, Period.TEN_MINS, OfferSide.BID, 0).getTime();
        Object[] indicatorResult = indicators.calculateIndicator(selectedInstrument, Period.TEN_MINS, new OfferSide[] {OfferSide.BID},
                "SMA", new IIndicators.AppliedPrice[] {IIndicators.AppliedPrice.CLOSE}, new Object[] {30}, Filter.WEEKENDS, 2, time, 0);
        double smaSmallCurrent = ((double[]) indicatorResult[0])[1];
        double smaSmallPrev = ((double[]) indicatorResult[0])[0];

        time = history.getBar(selectedInstrument, Period.ONE_HOUR, OfferSide.BID, 0).getTime();
        indicatorResult = indicators.calculateIndicator(selectedInstrument, Period.ONE_HOUR, new OfferSide[] {OfferSide.BID},
                "SMA", new IIndicators.AppliedPrice[] {IIndicators.AppliedPrice.CLOSE}, new Object[] {30}, Filter.WEEKENDS, 2, time, 0);
        double smaBigCurrent = ((double[]) indicatorResult[0])[1];
        double smaBigPrev = ((double[]) indicatorResult[0])[0];

        if (smaSmallCurrent < smaBigCurrent && smaBigCurrent > smaBigPrev) {
            double stopLoss = tick.getAsk() + selectedInstrument.getPipValue() * stopLossPips;
            double takeProfit = tick.getAsk() - selectedInstrument.getPipValue() * takeProfitPips;

            engine.submitOrder(getLabel(tick.getTime()), selectedInstrument, IEngine.OrderCommand.SELL,
                    tradeAmount, 0, slippage,  stopLoss, takeProfit, 0, "");

        } else if (smaSmallCurrent > smaBigCurrent && smaBigCurrent < smaBigPrev) {
            double stopLoss = tick.getBid() - selectedInstrument.getPipValue() * stopLossPips;
            double takeProfit = tick.getBid() + selectedInstrument.getPipValue() * takeProfitPips;

            engine.submitOrder(getLabel(tick.getTime()), selectedInstrument, IEngine.OrderCommand.BUY,
                    tradeAmount, 0, slippage,  stopLoss, takeProfit, 0, "");
        }
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
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

