package skaro.pokedex.data_processor;

//import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang.WordUtils;

public class TextFormatter 
{
	public static String flexFormToProper(String string)
	{
		String noDashes = string.replace("-", " ");
		return WordUtils.capitalize(noDashes);
	}
	
	public static String formatGeneration(String string)
	{
		String[] words = string.split("-");
		return WordUtils.capitalize(words[0]) + " " + words[1].toUpperCase();
	}
	
	public static String[] getURLComponents(String url)
	{
		return url.split("/");
	}
}
