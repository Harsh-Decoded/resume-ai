package com.finlogic.resume_ai.config;

import com.finlogic.resume_ai.model.NvidiaEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RagConfig {

    @Value("classpath:/static/data/extractedtext.txt")
    private Resource resumeText;

    @Value("vectorStore.json")
    private String vecStoreName;

    // Ensure property sources are loaded correctly
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    // EmbeddingModel bean configuration, WebClient.Builder is injected from WebClientConfig
    @Bean
    public EmbeddingModel embeddingModel(@Value("${api.key}") String apiKey, WebClient.Builder webClientBuilder) {
        return new NvidiaEmbeddingModel(apiKey, webClientBuilder);
    }

    // SimpleVectorStore bean that handles vector loading, saving, and transformations
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // Check if vector file exists, if not, process text and create vectors
//        File vectorFile = getVectorFile();
//        if (vectorFile.exists()) {
//            simpleVectorStore.load(vectorFile); // Load vectors if they exist
//        } else {
//            // Read text from file and split it into documents for embeddings
//            TextReader readText = new TextReader(resumeText);
//            List<Document> docs = readText.get();
//            TokenTextSplitter splitText = new TokenTextSplitter();
//            List<Document> splitDocs = splitText.apply(docs);
//
//            // Add the documents to the vector store and save them
//            simpleVectorStore.add(splitDocs);
//            simpleVectorStore.save(vectorFile);
//        }

        return simpleVectorStore;
    }


//    private void processMultipleResumeFiles(SimpleVectorStore simpleVectorStore){
//        // multiple files to be processed
//        File resumeDir=new File("src/main/resources/static/data/resumes/");
//        if(resumeDir.exists()){
//            // create array of pathnames denoted the files in the directory which satisfies the .txt extension
//            File[] resumeFiles=resumeDir.listFiles(((dir, name) -> name.endsWith(".txt")));
//
//        }
//    }

    //process each file individually and create vector
//    private void processSingleRes(File resFile, SimpleVectorStore simpleVectorStore){
//
//        //vector file path for a single resume
//        File vectFile=getVectForResume(resFile.getName());
//
//        TextReader textRead= new TextReader(resFile.toURI().toString());
//        List<Document> docs= textRead.get();
//
//        if(docs.isEmpty()){
//            LOGGER
//        }
//
//
//
//    }


    //get the vector file for a specific resume
//    private File getVectForResume(String fileName){
//        String vectorFileName = fileName.replaceAll("[^a-zA-Z0-9]", "_") + "_vectors.json";
//        return new File(Paths.get("src", "main", "resources", "static", "embedData", vectorFileName).toString());
//
//    }

    // Utility method to get the vector file (create it if it doesn't exist)
//    private File getVectorFile() {
//        Path path = Paths.get("src", "main", "resources", "static", "embedData");
//        File dir = path.toFile();
//        if (!dir.exists()) {
//            dir.mkdirs(); // Create the directory if it doesn't exist
//        }
//        String absPath = dir.getAbsolutePath() + "/" + vecStoreName;
//        return new File(absPath);
//    }
}
