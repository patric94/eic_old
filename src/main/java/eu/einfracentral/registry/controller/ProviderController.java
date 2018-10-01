package eu.einfracentral.registry.controller;

import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.Provider;
import eu.einfracentral.domain.Service;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.registry.service.ProviderService;
import eu.openminted.registry.core.domain.Paging;
import eu.openminted.registry.core.exception.ResourceNotFoundException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("provider")
@Api(value = "Get information about a Provider")
public class ProviderController extends ResourceController<Provider, Authentication> {

    private ProviderService<Provider, Authentication> providerManager;
    private InfraServiceService<InfraService, InfraService> infraService;

    @Autowired
    ProviderController(ProviderService<Provider, Authentication> service,
                       InfraServiceService<InfraService, InfraService> infraService) {
        super(service);
        this.providerManager = service;
        this.infraService = infraService;
    }

    @ApiOperation(value = "Get provider’s data providing the provider id")
    @RequestMapping(path = "{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Provider> get(@PathVariable("id") String id, Authentication jwt) throws ResourceNotFoundException {
        Provider provider = providerManager.get(id);
        provider.setUsers(null);
        return new ResponseEntity<>(provider, HttpStatus.OK);
    }

    @ApiOperation(value = "Creates a new Provider")
    @RequestMapping(method = RequestMethod.POST, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Provider> add(@RequestBody Provider provider, Authentication jwt) throws Exception {
        return super.add(provider, jwt);
    }

    @ApiOperation(value = "Updates Provider info")
    @RequestMapping(method = RequestMethod.PUT, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_PROVIDER') and @securityService.providerIsActive(#jwt,#provider)")
    public ResponseEntity<Provider> update(@RequestBody Provider provider, Authentication jwt) throws Exception {
        return super.update(provider, jwt);
    }

    @ApiIgnore
    @ApiOperation(value = "Get a list of all infraService providers in the catalogue")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "query", value = "Keyword to refine the search", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "from", value = "Starting index in the resultset", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "quantity", value = "Quantity of providers to be fetched", dataType = "string", paramType = "query")
    })
    @RequestMapping(path = "all", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
//    @PreAuthorize("hasRole('ROLE_ADMIN')") // TODO
    public ResponseEntity<Paging<Provider>> getAll(@ApiIgnore @RequestParam Map<String, Object> allRequestParams, Authentication jwt) throws ResourceNotFoundException {
        return super.getAll(allRequestParams, jwt);
    }

    @ApiOperation(value = "Get a list of services offered by a provider")
    @RequestMapping(path = "services/{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<List<Service>> getServices(@PathVariable("id") String id, Authentication jwt) {
        return new ResponseEntity<>(providerManager.getServices(id), HttpStatus.OK);
    }

    @ApiIgnore // TODO enable in a future release
    @ApiOperation(value = "Get a featured infraService offered by a provider")
    @RequestMapping(path = "featured/{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Service> getFeaturedService(@PathVariable("id") String id) {
        return new ResponseEntity<>(providerManager.getFeaturedService(id), HttpStatus.OK);
    }


    @ApiOperation(value = "Get a list of providers in which the given user is an admin")
    @RequestMapping(path = "getMyServiceProviders", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<List<Provider>> getMyServiceProviders(@RequestParam("email") String email) {
        return new ResponseEntity<>(providerManager.getMyServiceProviders(email), HttpStatus.OK);
    }

    @ApiOperation(value = "Get the pending services of the given provider")
    @RequestMapping(path = "services/pending/{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<List<Service>> getInactiveServices(@PathVariable("id") String id, Authentication jwt) {
        // TODO: move getPending to ProviderService
        List<Service> ret = providerManager.getInactiveServices(id).stream().map(Service::new).collect(Collectors.toList());
        return new ResponseEntity<>(ret, HttpStatus.OK);
    }

    @ApiOperation(value = "Get inactive providers")
    @RequestMapping(path = "inactive/all", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<List<Provider>> getInactive(Authentication jwt) {
        List<Provider> ret = providerManager.getInactive();
        return new ResponseEntity<>(ret, HttpStatus.OK);
    }
}
