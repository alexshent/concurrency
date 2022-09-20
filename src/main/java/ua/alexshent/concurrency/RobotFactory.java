package ua.alexshent.concurrency;

import java.util.Random;
import java.util.logging.*;

public class RobotFactory {
    private static final Logger logger = Logger.getLogger(RobotFactory.class.getName());
    private final Random random = new Random();
    private final MutatorInteger fuel = new MutatorInteger();
    private final MutatorInteger basicConstructionProgress = new MutatorInteger();
    private final MutatorInteger loadFirmwareProgress = new MutatorInteger();
    private final MutatorInteger finalOperationProgress = new MutatorInteger();
    private final Object loadFirmwareLock = new Object();
    private final Object finalOperationLock = new Object();
    private volatile boolean extractFuel = true;

    public RobotFactory() {
        logger.setUseParentHandlers(false);
        Handler handler = new ConsoleHandler();
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                StringBuilder sb = new StringBuilder();
                sb.append(logRecord.getLevel()).append(':');
                sb.append(logRecord.getMessage()).append('\n');
                return sb.toString();
            }
        };
        handler.setFormatter(formatter);
        logger.addHandler(handler);
    }

//    Робот 1 - делает фоновую работу - добывает топливо для фабрики
//    За одну итерация робот может добыть 500-1000 галлонов топлива, после чего тратит 3 секунд на транспортировку.
    private final Runnable fuelExtractor = () -> {
        final String name = "fuel extractor";
        final int delay = 3000;
        final int delta = 500;
        final String prefix = Thread.currentThread().getName() + " : " + name;
        try {
            while (extractFuel) {
                int production = random.nextInt(delta + 1) + delta;
                int fuelLevel = fuel.addAndGet(production);
                synchronized (fuel) {
                    fuel.notifyAll();
                }
                String message = String.format("%s : fuel level = %d", prefix, fuelLevel);
                logger.log(Level.INFO, message);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        String message = String.format("%s: finished", prefix);
        logger.log(Level.INFO, message);
    };

//    Робот 2 и Робот 3 - собирают базовую конструкцию детали
//    За одну итерацию они могут выполнить работу на 10-20 пунктов
//    После чего происходит перезагрузка на 2 секунд
//    Работа считается законченной когда достигнуто 100+ пунктов
    private final Runnable basicConstructionAssembler = () -> {
        final String name = "basic construction assembler";
        final int delay = 2000;
        final int delta = 10;
        final int maxProgress = 100;
        final String prefix = Thread.currentThread().getName() + " : " + name;
        try {
            while (basicConstructionProgress.getValue() < maxProgress) {
                int progress = random.nextInt(delta + 1) + delta;
                int progressLevel = basicConstructionProgress.addAndGet(progress);
                String message = String.format("%s : basic construction progress = %d", prefix, progressLevel);
                logger.log(Level.INFO, message);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        synchronized (loadFirmwareLock) {
            loadFirmwareLock.notifyAll();
        }
        String message = String.format("%s: finished", prefix);
        logger.log(Level.INFO, message);
    };

//    Робот 4 - начинает свою работу после завершения работы Роботов 2 и 3
//    За одну итерацию робот программирует микросхемы в диапазоне 25-35 пунктов
//    Перезагрузка 1 секунды
//    С вероятностью 30% робот может сломать микросхему и ему придется начать заново весь процесс работы
//    Работа считается законченной когда достигнуто 100+ пунктов
    private final Runnable firmwareLoader = () -> {
        final String name = "firmware loader";
        final int delay = 1000;
        final int progressDelta = 10;
        final int maxProgress = 100;
        final int faultProbability = 30;
        final String prefix = Thread.currentThread().getName() + " : " + name;
        try {
            synchronized (loadFirmwareLock) {
                loadFirmwareLock.wait();
            }
            while (loadFirmwareProgress.getValue() < maxProgress) {
                int progress = random.nextInt(progressDelta + 1) + 25;
                boolean fault = random.nextInt(100) < faultProbability;
                if (!fault) {
                    int progressLevel = loadFirmwareProgress.addAndGet(progress);
                    String message = String.format("%s: firmware loader progress %d", prefix, progressLevel);
                    logger.log(Level.INFO, message);
                }
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        synchronized (finalOperationLock) {
            finalOperationLock.notifyAll();
        }
        String message = String.format("%s: finished", prefix);
        logger.log(Level.INFO, message);
    };

//    Робот 5 - начинает свою работу после завершения работы Роботов 4, формирует деталь в готовый вид,
//    но для этого ему нужно топливо, добытое роботом 1
//    За одну итерацию робот продвигается на 10 пунктов
//    Перезагрузка 1 секунды
//    Одна итерация требует 350-700 топлива
//    Если топлива нет то робот засыпает до пополнения резервов
//    Работа считается законченной когда достигнуто 100+ пунктов
    private final Runnable finalOperationDoer = () -> {
        final String name = "final operation doer";
        final int delay = 1000;
        final int fuelConsumptionDelta = 350;
        final int maxProgress = 100;
        final int progressDelta = 10;
        final String prefix = Thread.currentThread().getName() + " : " + name;
        try {
            synchronized (finalOperationLock) {
                finalOperationLock.wait();
            }
            while (finalOperationProgress.getValue() < maxProgress) {
                int fuelConsumption = random.nextInt(fuelConsumptionDelta + 1) + fuelConsumptionDelta;
                while (fuel.getValue() < fuelConsumption) {
                    synchronized (fuel) {
                        fuel.wait();
                    }
                }
                fuel.addAndGet(-fuelConsumption);
                int progressLevel = finalOperationProgress.addAndGet(progressDelta);
                String message = String.format("%s: final operation progress %d", prefix, progressLevel);
                logger.log(Level.INFO, message);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        extractFuel = false;
        String message = String.format("%s: finished", prefix);
        logger.log(Level.INFO, message);
    };

    public void start() {
        Thread robot1 = new Thread(fuelExtractor);

        Thread robot2 = new Thread(basicConstructionAssembler);
        Thread robot3 = new Thread(basicConstructionAssembler);

        Thread robot4 = new Thread(firmwareLoader);

        Thread robot5 = new Thread(finalOperationDoer);

        robot1.start();
        robot2.start();
        robot3.start();
        robot4.start();
        robot5.start();
    }
}
