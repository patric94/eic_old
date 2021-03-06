package eu.einfracentral.registry.manager;

import eu.einfracentral.config.security.EICAuthoritiesMapper;
import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.Provider;
import eu.einfracentral.domain.Service;
import eu.einfracentral.domain.User;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.registry.service.ProviderService;
import eu.einfracentral.service.RegistrationMailService;
import eu.einfracentral.service.SecurityService;
import eu.openminted.registry.core.domain.Browsing;
import eu.openminted.registry.core.domain.FacetFilter;
import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.exception.ResourceNotFoundException;
import eu.openminted.registry.core.service.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import static eu.einfracentral.config.CacheConfig.CACHE_PROVIDERS;

@org.springframework.stereotype.Service("providerManager")
public class ProviderManager extends ResourceManager<Provider> implements ProviderService<Provider, Authentication> {

    private static final Logger logger = LogManager.getLogger(ProviderManager.class);
    private InfraServiceService<InfraService, InfraService> infraServiceService;
    private SecurityService securityService;
    private Random randomNumberGenerator;
    private RegistrationMailService registrationMailService;
    private EICAuthoritiesMapper eicAuthoritiesMapper;

    @Autowired
    public ProviderManager(@Lazy InfraServiceService<InfraService, InfraService> infraServiceService,
                           @Lazy SecurityService securityService, Random randomNumberGenerator,
                           @Lazy RegistrationMailService registrationMailService, /*JmsTemplate jmsTopicTemplate*/
                           @Lazy EICAuthoritiesMapper eicAuthoritiesMapper) {
        super(Provider.class);
        this.infraServiceService = infraServiceService;
        this.securityService = securityService;
        this.randomNumberGenerator = randomNumberGenerator;
        this.registrationMailService = registrationMailService;
        this.eicAuthoritiesMapper = eicAuthoritiesMapper;
    }


    @Override
    public String getResourceType() {
        return "provider";
    }

    @Override
    @CacheEvict(value = CACHE_PROVIDERS, allEntries = true)
    public Provider add(Provider provider, Authentication auth) {
        List<User> users;
        User authUser = new User(auth);
        Provider ret;
        if (provider.getId() == null) {
            provider.setId(provider.getName());
        }
        provider.setId(StringUtils
                .stripAccents(provider.getId())
                .replaceAll("[^a-zA-Z0-9\\s\\-\\_]+", "")
                .replaceAll(" ", "_"));
        if ("".equals(provider.getId())) {
            throw new ServiceException("Provider id not valid. Special characters are ignored.");
        }

        users = provider.getUsers();
        if (users == null) {
            users = new ArrayList<>();
        }
        if (users.stream().noneMatch(u -> u.getEmail().equals(authUser.getEmail()))) {
            users.add(authUser);
            provider.setUsers(users);
        }
        provider.setActive(false);
        provider.setStatus(Provider.States.PENDING_1.getKey());

        ret = super.add(provider, null);
        logger.debug("Adding Provider " + provider);

        // update provider roles
        eicAuthoritiesMapper.updateAuthorities();

        // send messages to queue
        registrationMailService.sendProviderMails(provider);

        return ret;
    }

    @Override
    @CacheEvict(value = CACHE_PROVIDERS, allEntries = true)
    public Provider update(Provider provider, Authentication auth) {
        Resource existing = whereID(provider.getId(), true);
        Provider ex = deserialize(existing);
        provider.setActive(ex.getActive());
        provider.setStatus(ex.getStatus());
        existing.setPayload(serialize(provider));
        existing.setResourceType(resourceType);
        resourceService.updateResource(existing);
        logger.debug("Updating Provider " + provider);

        // update provider roles
        eicAuthoritiesMapper.updateAuthorities();

        return provider;
    }

    /**
     * Do not expose this method to users because it returns sensitive information about providers.
     *
     * @param id
     * @return
     */
    @Override
    @Cacheable(value = CACHE_PROVIDERS)
    public Provider get(String id) {
        Provider provider = super.get(id);
        if (provider == null) {
            throw new eu.einfracentral.exception.ResourceNotFoundException(
                    String.format("Could not find provider with id: %s", id));
        }
        return provider;
    }

    @Override
    @Cacheable(value = CACHE_PROVIDERS)
    public Provider get(String id, Authentication auth) {
        Provider provider = get(id);
        if (auth == null) {
            provider.setUsers(null);
        } else if (securityService.hasRole(auth, "ROLE_ADMIN")) {
            return provider;
        } else if (securityService.hasRole(auth, "ROLE_PROVIDER")
                && securityService.userIsProviderAdmin(auth, provider)) {
            return provider;
        }
        provider.setUsers(null);
        return provider;
    }

    @Override
    @Cacheable(value = CACHE_PROVIDERS)
    public Browsing<Provider> getAll(FacetFilter ff, Authentication auth) {
        List<Provider> userProviders = null;
        if (auth != null && auth.isAuthenticated()) {
            if (securityService.hasRole(auth, "ROLE_ADMIN")) {
                return super.getAll(ff, auth);
            }
            // if user is not an admin, check if he is a provider
            userProviders = getMyServiceProviders(auth);
        }

        // retrieve providers
        Browsing<Provider> providers = super.getAll(ff, auth);

        // create a list of providers without their users
        List<Provider> modified = providers.getResults()
                .stream()
                .map(p -> {
                    p.setUsers(null);
                    return p;
                })
                .collect(Collectors.toList());

        if (userProviders != null) {
            // replace user providers having null users with complete provider entries
            userProviders.forEach(x -> {
                modified.removeIf(provider -> provider.getId().equals(x.getId()));
                modified.add(x);
            });
        }
        providers.setResults(modified);
        return providers;
    }

    @Override
    @CacheEvict(value = CACHE_PROVIDERS, allEntries = true)
    public void delete(Provider provider) {
        List<InfraService> services = this.getInfraServices(provider.getId());
        services.forEach(s -> {
            try {
                if (s.getProviders().size() == 1) {
                    infraServiceService.delete(s);
                }
            } catch (ResourceNotFoundException e) {
                logger.error("Error deleting Service", e);
            }
        });
        super.delete(provider);
        logger.debug("Deleting Provider " + provider);

        // update provider roles
        eicAuthoritiesMapper.updateAuthorities();
    }

    @Override
    @CacheEvict(value = CACHE_PROVIDERS, allEntries = true)
    public Provider verifyProvider(String id, Provider.States status, Boolean active, Authentication auth) {
        Provider provider = get(id);
        provider.setStatus(status.getKey());
        switch (status) {
            case APPROVED:
                if (active == null) {
                    active = true;
                }
                provider.setActive(active);
                break;

            default:
                provider.setActive(false);
        }

        // send registration emails
        registrationMailService.sendProviderMails(provider);

        if (active != null) {
            provider.setActive(active);
            if (!active) {
                deactivateServices(provider.getId());
            } else {
                activateServices(provider.getId());
            }
        }
        logger.info("Verifying Provider " + provider);
        return super.update(provider, auth);
    }

    @Override
    @Cacheable(value = CACHE_PROVIDERS)
    public List<Provider> getServiceProviders(String email, Authentication auth) {
        List<Provider> providers;
        if (auth == null) {
//            return null; // TODO: enable this when front end can handle 401 properly
            return new ArrayList<>();
        } else if (securityService.hasRole(auth, "ROLE_ADMIN")) {
            FacetFilter ff = new FacetFilter();
            ff.setQuantity(10000);
            providers = super.getAll(ff, null).getResults();
        } else if (securityService.hasRole(auth, "ROLE_PROVIDER")) {
            providers = getMyServiceProviders(auth);
        } else {
            return new ArrayList<>();
        }
        return providers
                .stream()
                .map(p -> {
                    if (p.getUsers() != null && p.getUsers().stream().filter(Objects::nonNull).anyMatch(u -> {
                        if (u.getEmail() != null) {
                            return u.getEmail().equals(email);
                        }
                        return false;
                    })) {
                        return p;
                    } else return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = CACHE_PROVIDERS)
    public List<Provider> getMyServiceProviders(Authentication auth) {
        if (auth == null) {
//            return null; // TODO: enable this when front end can handle 401 properly
            return new ArrayList<>();
        }
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        return super.getAll(ff, null).getResults()
                .stream().map(p -> {
                    if (securityService.userIsProviderAdmin(auth, p)) {
                        return p;
                    } else return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<InfraService> getInfraServices(String providerId) {
        FacetFilter ff = new FacetFilter();
        ff.addFilter("providers", providerId);
        ff.setQuantity(10000);
        return infraServiceService.getAll(ff, null).getResults();
    }

    @Override
    public List<Service> getServices(String providerId) {
        FacetFilter ff = new FacetFilter();
        ff.addFilter("providers", providerId);
        ff.addFilter("latest", "true");
        ff.setQuantity(10000);
        return infraServiceService.getAll(ff, null).getResults().stream().map(Service::new).collect(Collectors.toList());
    }

    @Override
    public List<Service> getActiveServices(String providerId) {
        FacetFilter ff = new FacetFilter();
        ff.addFilter("providers", providerId);
        ff.addFilter("active", "true");
        ff.addFilter("latest", "true");
        ff.setQuantity(10000);
        return infraServiceService.getAll(ff, null).getResults().stream().map(Service::new).collect(Collectors.toList());
    }

    //Gets random Services to be featured at the Carousel
    @Override
    public Service getFeaturedService(String providerId) {
        // TODO: change this method
        List<Service> services = getServices(providerId);
        Service featuredService = null;
        if (!services.isEmpty()) {
            featuredService = services.get(randomNumberGenerator.nextInt(services.size()));
        }
        return featuredService;
    }

    @Override
    public List<Provider> getInactive() {
        FacetFilter ff = new FacetFilter();
        ff.addFilter("active", false);
        ff.setFrom(0);
        ff.setQuantity(10000);
        return getAll(ff, null).getResults();
    }

    @Override
    public List<InfraService> getInactiveServices(String providerId) {
        FacetFilter ff = new FacetFilter();
        ff.addFilter("providers", providerId);
        ff.addFilter("active", false);
        ff.setFrom(0);
        ff.setQuantity(10000);
        return infraServiceService.getAll(ff, null).getResults();
    }

    public void activateServices(String providerId) { // TODO: decide how to use service.status variable
        List<InfraService> services = this.getInfraServices(providerId);
        logger.info("Activating all Services of the Provider with id " + providerId);
        for (InfraService service : services) {
//            service.setActive(service.getStatus() == null || service.getStatus().equals("true"));
//            service.setStatus(null);
            service.setActive(true);
            try {
                infraServiceService.update(service, null);
                logger.info("Setting Service " + service.getName() + " as active");
            } catch (ResourceNotFoundException e) {
                logger.error("Could not update service " + service.getName());
            }
        }
    }

    public void deactivateServices(String providerId) { // TODO: decide how to use service.status variable
        List<InfraService> services = this.getInfraServices(providerId);
        for (InfraService service : services) {
//            service.setStatus(service.isActive() != null ? service.isActive().toString() : "true");
//            service.setStatus(null);
            service.setActive(false);
            try {
                infraServiceService.update(service, null);
                logger.info("Setting Service " + service.getName() + " as inactive");
            } catch (ResourceNotFoundException e) {
                logger.error("Could not update service " + service.getName());
            }
        }
    }

    /**
     * This method is used to update a list of new providers with status 'Provider.States.ST_SUBMISSION'
     * to status 'Provider.States.PENDING_2'
     * @param providers
     * @param authentication
     */
    @CacheEvict(value = CACHE_PROVIDERS, allEntries = true)
    public void verifyNewProviders(List<String> providers, Authentication authentication) {
        for (String serviceProvider : providers) {
            Provider provider = get(serviceProvider);
            if (provider.getStatus().equals(Provider.States.ST_SUBMISSION.getKey())) {
                verifyProvider(provider.getId(), Provider.States.PENDING_2, false, authentication);
            }
        }
    }

}
