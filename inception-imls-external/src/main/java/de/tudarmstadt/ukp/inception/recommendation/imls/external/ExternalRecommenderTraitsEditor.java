/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.imls.external;

import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.feedback.IFeedbackMessageFilter;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.UrlValidator;


import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxFormComponentUpdatingBehavior;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.DefaultTrainableRecommenderTraitsEditor;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;

public class ExternalRecommenderTraitsEditor
    extends DefaultTrainableRecommenderTraitsEditor
{
    private static final long serialVersionUID = 1677442652521110324L;

    private static final String MID_FORM = "form";

    private @SpringBean RecommendationEngineFactory<ExternalRecommenderTraits> toolFactory;
    
    private final ExternalRecommenderTraits traits;

    public ExternalRecommenderTraitsEditor(String aId, IModel<Recommender> aRecommender)
    {
        super(aId, aRecommender);
        
        traits = toolFactory.readTraits(aRecommender.getObject());

        Form<ExternalRecommenderTraits> form = new Form<ExternalRecommenderTraits>(MID_FORM,
                CompoundPropertyModel.of(Model.of(traits)))
        {
            private static final long serialVersionUID = -3109239605742291123L;

            @Override
            protected void onSubmit()
            {
                super.onSubmit();
                toolFactory.writeTraits(aRecommender.getObject(), traits);
            }
        };

        TextField<String> remoteUrl = new TextField<>("remoteUrl");
        remoteUrl.setRequired(true);
        
        remoteUrl.add(new ConnectionChecker());
        remoteUrl.add(new UrlValidator());
        form.add(remoteUrl);
        
        CheckBox trainable = new CheckBox("trainable");
        trainable.setOutputMarkupId(true);
        trainable.add(new LambdaAjaxFormComponentUpdatingBehavior("change", _target -> 
                _target.add(getTrainingStatesChoice())));
        form.add(trainable);
        
        getTrainingStatesChoice().add(visibleWhen(() -> trainable.getModelObject() == true));
        
        add(new FeedbackPanel("feedbackMessage", 
        		new ExactErrorLevelFilter(FeedbackMessage.ERROR)).setOutputMarkupId(true));
        add(new FeedbackPanel("succesMessage", 
        		new ExactErrorLevelFilter(FeedbackMessage.SUCCESS)).setOutputMarkupId(true));
        
        add(form);
    }
    
    
    class ExactErrorLevelFilter implements IFeedbackMessageFilter{
    	private int errorLevel;

		public ExactErrorLevelFilter(int errorLevel){
			this.errorLevel = errorLevel;
		}
		
		@Override
		public boolean accept(FeedbackMessage message) {
			return message.getLevel() == errorLevel;
		}
    }
    
    class ConnectionChecker implements IValidator<String>  {

		@Override
		public void validate(IValidatable<String> validatable){
			String urlRecieved = validatable.getValue();
			
			HttpURLConnection.setFollowRedirects(false);
		    HttpURLConnection con = null;
			try {
				con = (HttpURLConnection) new URL(urlRecieved).openConnection();
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    try {
				con.setRequestMethod("HEAD");
			} catch (ProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    
		    try {
				if(con.getResponseCode() != HttpURLConnection.HTTP_OK){
					ValidationError error = new ValidationError(this);
					error.setVariable("errorCode", 
							+con.getResponseCode());
					validatable.error(error);
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
		}
    
    }	
    
    
    
    }
    
    
    
    
        
