package skaro.pokedex.core;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.patreon.PatreonAPI;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Presence;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import skaro.pokedex.core.ServiceManager.ServiceManagerBuilder;
import skaro.pokedex.data_processor.CommandService;
import skaro.pokedex.data_processor.commands.AbilityCommand;
import skaro.pokedex.data_processor.commands.AboutCommand;
import skaro.pokedex.data_processor.commands.CommandsCommand;
import skaro.pokedex.data_processor.commands.CoverageCommand;
import skaro.pokedex.data_processor.commands.DataCommand;
import skaro.pokedex.data_processor.commands.DexCommand;
import skaro.pokedex.data_processor.commands.HelpCommand;
import skaro.pokedex.data_processor.commands.ItemCommand;
import skaro.pokedex.data_processor.commands.LearnCommand;
import skaro.pokedex.data_processor.commands.MoveCommand;
import skaro.pokedex.data_processor.commands.PatreonCommand;
import skaro.pokedex.data_processor.commands.RandpokeCommand;
import skaro.pokedex.data_processor.commands.SetCommand;
import skaro.pokedex.data_processor.commands.ShinyCommand;
import skaro.pokedex.data_processor.commands.StatsCommand;
import skaro.pokedex.data_processor.commands.WeakCommand;
import skaro.pokedex.data_processor.formatters.AbilityResponseFormatter;
import skaro.pokedex.data_processor.formatters.CoverageResponseFormatter;
import skaro.pokedex.data_processor.formatters.DataResponseFormatter;
import skaro.pokedex.data_processor.formatters.DexResponseFormatter;
import skaro.pokedex.data_processor.formatters.ItemResponseFormatter;
import skaro.pokedex.data_processor.formatters.LearnResponseFormatter;
import skaro.pokedex.data_processor.formatters.MoveResponseFormatter;
import skaro.pokedex.data_processor.formatters.RandpokeResponseFormatter;
import skaro.pokedex.data_processor.formatters.ShinyResponseFormatter;
import skaro.pokedex.data_processor.formatters.StatsResponseFormatter;
import skaro.pokedex.data_processor.formatters.WeakResponseFormatter;

public class PokedexV3 
{
	public static void main(String[] args) throws Exception
	{
		//Parse command line arguments
		if(args.length != 2)
		{
			System.out.println("Usage: <shard ID> <total shards>");
			System.exit(1);
		}
		
		//Record on command line arguments
		int shardIDToManage = -1;
		int totalShards = -1;
		try
		{ 
			shardIDToManage = Integer.parseInt(args[0]);
			totalShards = Integer.parseInt(args[1]);
		}
		catch(NumberFormatException e)
		{
			System.out.println("[Pokedex main] Error parsing command line arguments.");
			System.exit(1);
		}
		
		
		//Load configurations
		System.out.println("[Pokedex main] Loading configurations...");
		ScheduledExecutorService pokedexThreadPool = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 6);
		ConfigurationService configurationService = ConfigurationService.initialize(ConfigurationType.DEVELOP);
		CommandService commandMap = new CommandService(pokedexThreadPool);
		
		PokedexManager manager = PokedexManager.PokedexConfigurator.newInstance()
								.withService(commandMap)
								.withService(createDiscordService(configurationService, totalShards, shardIDToManage))
								.withService(createPatreonService(configurationService, pokedexThreadPool))
								.withService(new ColorService())
								.withService(new EmojiService())
								.withService(createPokeFlexService(configurationService, pokedexThreadPool))
								.withService(new TTSConverter())
								.configure();
		
		populateCommandMap(manager, commandMap);
		
		DiscordService service = (DiscordService)manager.getService(ServiceType.DISCORD);
		DiscordClient client = service.getV3Client();
		Scheduler scheduler = Schedulers.fromExecutorService(pokedexThreadPool);
		
		client.getEventDispatcher().on(MessageCreateEvent.class).publishOn(scheduler) // This listens for all events that are of MessageCreateEvent
        .subscribe(event -> event.getMessage().getContent().ifPresent(c -> System.out.println(c))); // "subscribe" is the method you need to call to actually make sure that it's doing something.

		client.login().block(); 
	}
	
	private static DiscordService createDiscordService(ConfigurationService configService, int shardID, int shardCount)
	{
		Optional<String> discordToken = configService.getAuthToken("discord");
		DiscordClient discordClient = new DiscordClientBuilder(discordToken.get())
							.setShardCount(shardCount)
							.setShardIndex(shardID)
							.setInitialPresence(Presence.online())
							.build();
		return new DiscordService(discordClient);
	}
	
	private static PerkChecker createPatreonService(ConfigurationService configService, ScheduledExecutorService threadPool)
	{
		Optional<String> patreonAccessToken = configService.getConfigData("access_token", "patreon");
		PatreonAPI patreonClient = new PatreonAPI(patreonAccessToken.get());
		return new PerkChecker(patreonClient, threadPool);
	}
	
	private static PokeFlexService createPokeFlexService(ConfigurationService configService, ScheduledExecutorService threadPool)
	{
		return new PokeFlexService(configService.getPokeFlexURL(), threadPool);
	}
	
	private static void populateCommandMap(PokedexManager manager, CommandService commandService) throws ServiceException, ServiceConsumerException
	{
		ServiceManagerBuilder commandServiceBuilder = ServiceManager.ServiceManagerBuilder.newInstance(manager)
				.addService(ServiceType.COLOR);
		ServiceManagerBuilder formatterServiceBuilder = ServiceManager.ServiceManagerBuilder.newInstance(manager)
				.addService(ServiceType.COLOR);
		
		//ColorService
		commandService.addCommand(new AboutCommand(commandServiceBuilder.build()));
		commandService.addCommand(new PatreonCommand(commandServiceBuilder.build()));
		
		//ColorService, CommandService
		commandServiceBuilder.addService(ServiceType.COMMAND);
		commandService.addCommand(new HelpCommand(commandServiceBuilder.build()));
		commandService.addCommand(new CommandsCommand(commandServiceBuilder.build()));
		
		//ColorService, ConfigService
		commandServiceBuilder.removeService(ServiceType.COMMAND);
		commandServiceBuilder.addService(ServiceType.CONFIG);
		commandService.addCommand(new AboutCommand(commandServiceBuilder.build()));
		
		//ColorService, PokeFlexService
		commandServiceBuilder.removeService(ServiceType.CONFIG);
		commandServiceBuilder.addService(ServiceType.POKE_FLEX);
		commandService.addCommand(new ItemCommand(commandServiceBuilder.build(), new ItemResponseFormatter()));
		commandService.addCommand(new MoveCommand(commandServiceBuilder.build(), new MoveResponseFormatter()));
		commandService.addCommand(new CoverageCommand(commandServiceBuilder.build(), new CoverageResponseFormatter()));
		
		//ColorService, PokeFlexService, PerkService
		commandServiceBuilder.addService(ServiceType.PERK);
		commandService.addCommand(new AbilityCommand(commandServiceBuilder.build(), new AbilityResponseFormatter()));
		commandService.addCommand(new DataCommand(commandServiceBuilder.build(), new DataResponseFormatter()));
		commandService.addCommand(new DexCommand(commandServiceBuilder.build(), new DexResponseFormatter()));
		commandService.addCommand(new LearnCommand(commandServiceBuilder.build(), new LearnResponseFormatter()));
		commandService.addCommand(new RandpokeCommand(commandServiceBuilder.build(), new RandpokeResponseFormatter()));
		commandService.addCommand(new SetCommand(commandServiceBuilder.build()));
		commandService.addCommand(new ShinyCommand(commandServiceBuilder.build(), new ShinyResponseFormatter()));
		commandService.addCommand(new StatsCommand(commandServiceBuilder.build(), new StatsResponseFormatter()));
		commandService.addCommand(new WeakCommand(commandServiceBuilder.build(), new WeakResponseFormatter()));
	}
	
}
