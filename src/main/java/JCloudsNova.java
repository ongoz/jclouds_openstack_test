import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.compute.functions.AllocateAndAddFloatingIpToNode;
import org.jclouds.openstack.nova.v2_0.compute.loaders.LoadFloatingIpsForInstance;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.regionscoped.RegionAndId;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class JCloudsNova implements Closeable {
    private static final int INCONSISTENCY_WINDOW = 5000;
    private final NovaApi novaApi;
    private final Set<String> regions;

/*    private final ComputeService computeService;*/
    //https://github.com/jclouds/jclouds-examples/blob/master/rackspace/src/main/java/org/jclouds/examples/rackspace/cloudservers/ListServersWithFiltering.java

    public static void main(String[] args) throws IOException {
        JCloudsNova jcloudsNova = new JCloudsNova();

        try {
 //           jcloudsNova.listServers();
 //           jcloudsNova.loadFloatingIpsForInstance("c27149f4-5a6b-4577-8c10-a95f5060c0d4getInstancesInfo");
//            jcloudsNova.getInstanceInfo("ef9484e8-99e2-4878-b1e9-0785fadc5415");
 //           jcloudsNova.terminateInstance("9da274bf-5a42-4f0c-ac2c-d5ee09e76c0a");
//            jcloudsNova.stopServers();
//            jcloudsNova.createInstance();
//            System.out.println(jcloudsNova.getInstancesInfo().get(1).getId());
//            System.out.println(jcloudsNova.instanceNameToId("test_api_create_instance", jcloudsNova.getInstancesInfo()));
            jcloudsNova.addFloatingIp();
            jcloudsNova.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jcloudsNova.close();
        }
    }

    public JCloudsNova() {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());

        String provider = "openstack-nova";
        String identity = "admin:admin"; // tenantName:userName
        String credential = "3f4a0c469aa9451d";

        novaApi = ContextBuilder.newBuilder(provider)
                .endpoint("http://10.0.1.65:5000/v2.0/")
                .credentials(identity, credential)
                .modules(modules)
                .buildApi(NovaApi.class);
        regions = novaApi.getConfiguredRegions();

/*       ComputeServiceContext context = ContextBuilder.newBuilder(PROVIDER)
                .credentials(username, apiKey)
                .buildView(ComputeServiceContext.class);
        computeService = context.getComputeService();*/

    }

    //인스턴스의 이름과 전체 인스턴스의 리스트를 전달하면 인스턴스의 ID출력
    public String instanceNameToId(String instanceName,List<Server> servers){
        for(Server server: servers){
            if(server.getName().equals(instanceName))
                return server.getId();
        }
        return null;
    }

    //현재 서버 전체의 리스트를 출력
    private void listServers() {
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);

            for (Server server : serverApi.listInDetail().concat()) {
                System.out.printf(server.toString());
            }
        }
    }

    //현재 서버 전체의 리스트를 list에 담아서 리턴
    private List<Server> getInstancesInfo(){
        List<Server> servers = new ArrayList<Server>();
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);

            for (Server server : serverApi.listInDetail().concat()) {
                servers.add(server);
            }
        }
        return servers;
    }

    //인스턴스 id를 입력, 해당 인스턴스의 정보를 출력
    private void getInstanceInfo(String instanceId) {
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);
            if(serverApi.get(instanceId) != null)
                System.out.println(serverApi.get(instanceId).toString());
        }
    }

    //인스턴스 id를 입력, 해당 인스턴스의 객체를 리턴(Server)
    public Server getInstanceObject(String instanceId) {
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);
            if(serverApi.get(instanceId) != null)
                return serverApi.get(instanceId);
        }
        return null;
    }

    //인스턴스 id를 입력, 해당 인스턴스를 정지(shut down)
    private void stopInstance(String instanceId) {
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);
            try {
                serverApi.stop(instanceId);
            }catch(Exception e){
                System.out.println(e.toString()); //로거로 error 쏴주어야 함..
            }
        }
    }

    //인스턴스 id를 입력, 해당 인스턴스를 정지(terminate)
    private void terminateInstance(String instatnceId) {
        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);
            try {
                serverApi.delete(instatnceId);
            }catch(Exception e){
                System.out.println(e.toString()); //로거로 error 쏴주어야 함..
            }
        }
    }

    private void createInstance() {
        CreateServerOptions option = new CreateServerOptions();
        option.networks("30711dfc-49b3-4d28-b4b8-942847551db2");
        option.adminPass("1234");
        option.keyPairName("ubuntu_test");

        for (String region : regions) {
            ServerApi serverApi = novaApi.getServerApi(region);
            serverApi.create("test_api_create_instance", "19257f34-d638-4343-a0c7-c7e2f407ebff", "2", option);
        }
    }

   //인스턴스 id를 입력하면, 할당된 floating ip를 출력 (수정필요)
    private String loadFloatingIpsForInstance(String instatnceId) {
        for (String region : regions) {

            LoadFloatingIpsForInstance loadFloatingIpsForInstance = new LoadFloatingIpsForInstance(novaApi);
            RegionAndId regionAndId = null;
            regionAndId = regionAndId.fromSlashEncoded(region+"/"+instatnceId);

            try {
                Iterable tmp = loadFloatingIpsForInstance.load(regionAndId);
                Iterator iterator = tmp.iterator();
                while (iterator.hasNext()) {
                    Object element = iterator.next();
                    if(fromEqualsStringToMap("FloatingIP", element.toString()).get("ip").toString() != null)
                        return fromEqualsStringToMap("FloatingIP", element.toString()).get("ip").toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void addFloatingIp() throws Exception {
        for (String region : regions) {
            Optional<? extends FloatingIPApi> apiOption = novaApi.getFloatingIPApi(region);
            if (!apiOption.isPresent())
                continue;
            FloatingIPApi api = apiOption.get();
            ServerApi serverApi = this.novaApi.getServerApi(region);
            Server server = serverApi.get("34385ccd-7674-4e6f-bbfc-debaf259c1d0");
            FloatingIP floatingIP = null;
            for(int i=0,n=api.list().size();i<n;i++) {
                if (isFixedIPNull(api.list().get(i).toString()) == true) {
                    floatingIP = api.list().get(i);
                    break;
                }
                System.out.println("i="+i);
            }
            try {
                api.addToServer(floatingIP.getIp(), server.getId());
            } finally {
                /*api.removeFromServer(floatingIP.getIp(), server.getId());
                serverApi.delete(server.getId());*/
            }
        }
    }

    public void close() throws IOException {
        Closeables.close(novaApi, true);
    }

    public boolean isFixedIPNull(String ipdataToString){ //FloatingIP{id=d3c731a4-726d-492e-ace8-36393dd603a7, ip=10.0.1.185, fixedIp=null, instanceId=null, pool=public2}
        Map tmpMap = fromEqualsStringToMap("FloatingIP",ipdataToString);
        if(tmpMap.get("fixedIp").equals("null"))
            return true;
        return false;
    }


    //CustomString 파서..
    //FloatingIP{id=62c10231-126d-40db-aca3-989ab76e6a40, ip=10.0.1.183, fixedIp=10.0.0.25, instanceId=ef9484e8-99e2-4878-b1e9-0785fadc5415, pool=public2}
    //형식의 String을 map에 담음
    private Map fromEqualsStringToMap(String title,String inputString){
        Map<String, String> tmpMap = new HashMap<String, String>();
        String[] pairs = inputString.trim().substring(title.length()+1,inputString.length()-1).split(",");
        for (int i=0;i<pairs.length;i++) {
            String pair = pairs[i];
            String[] keyValue = pair.split("=");
            tmpMap.put(keyValue[0].trim(), keyValue[1].trim());
        }
        return tmpMap;
    }

}