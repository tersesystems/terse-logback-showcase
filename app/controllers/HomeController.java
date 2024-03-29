package controllers;

import models.Cat;
import play.mvc.Result;
import play.mvc.With;
import services.CatService;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends AbstractController {

    private final CatService catService;

    @Inject
    public HomeController(CatService catService) {
        this.catService = catService;
    }

    public Result index() {
        return ok(views.html.index.render());
    }

    @With(ContextAction.class)
    public CompletionStage<Result> show(Boolean error) {
        if (logger.isDebugEnabled()) {
            logger.debug("About to render / with error={}", error);
        }
        return catService.getCat(error).thenApply(cat -> ok(views.html.show.render(cat)));
    }
}
