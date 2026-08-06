package com.github.slmpc.lumingraphics.demo;

import com.github.slmpc.prismrhi.backend.vulkan.VulkanExternalContext;
import com.github.slmpc.prismrhi.backend.vulkan.VulkanFeature;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Full Vulkan instance/device/queue/surface fixture whose lifetime belongs to the demo.
 */
final class CallerOwnedVulkanContext implements AutoCloseable {
    private final int width;
    private final int height;
    private final RhiInvalidationToken invalidation = new RhiInvalidationToken();
    private Set<String> instanceExtensions = Set.of();
    private long window;
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private int queueFamily;
    private String deviceName = "";
    private int apiVersion;
    private boolean closed;

    private CallerOwnedVulkanContext(int width, int height) {
        this.width = width;
        this.height = height;
    }

    static CallerOwnedVulkanContext create(int width, int height) {
        if (!glfwInit()) throw new IllegalStateException("glfwInit failed");
        CallerOwnedVulkanContext fixture = new CallerOwnedVulkanContext(width, height);
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            fixture.window = glfwCreateWindow(width, height, "Lumin Vulkan smoke", NULL, NULL);
            if (fixture.window == NULL) throw new IllegalStateException("hidden Vulkan GLFW window creation failed");
            fixture.initialize();
            return fixture;
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    VulkanExternalContext externalContext() {
        return VulkanExternalContext.builder(instance, physicalDevice, device)
                .graphicsQueue(queue, queueFamily, 0)
                .computeQueue(queue, queueFamily, 0)
                .transferQueue(queue, queueFamily, 0)
                .enabledInstanceExtensions(instanceExtensions)
                .enabledDeviceExtensions(Set.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                .enabledFeatures(Set.of(VulkanFeature.DYNAMIC_RENDERING))
                .contextIdentity(new RhiContextIdentity(window, "lumin-vulkan-smoke"))
                .invalidation(invalidation)
                .surface(new VulkanExternalContext.Surface(surface, width, height))
                .synchronization(() -> {
                })
                .build();
    }

    String deviceName() {
        return deviceName;
    }

    int apiVersion() {
        return apiVersion;
    }

    int queueFamily() {
        return queueFamily;
    }

    boolean closed() {
        return closed;
    }

    void copyImageToBuffer(long image, long buffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                    .queueFamilyIndex(queueFamily);
            var poolPointer = stack.longs(NULL);
            check(vkCreateCommandPool(device, poolInfo, null, poolPointer), "vkCreateCommandPool(readback)");
            long pool = poolPointer.get(0);
            VkCommandBuffer command = null;
            try {
                VkCommandBufferAllocateInfo allocation = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                        .commandPool(pool)
                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandBufferCount(1);
                PointerBuffer commands = stack.mallocPointer(1);
                check(vkAllocateCommandBuffers(device, allocation, commands), "vkAllocateCommandBuffers(readback)");
                command = new VkCommandBuffer(commands.get(0), device);
                check(vkBeginCommandBuffer(command, VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer(readback)");
                VkImageMemoryBarrier.Buffer toTransfer = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image);
                toTransfer.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);
                VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, stack)
                        .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
                copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                        .baseArrayLayer(0).layerCount(1);
                copy.imageOffset().set(0, 0, 0);
                copy.imageExtent().set(width, height, 1);
                vkCmdCopyImageToBuffer(command, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
                VkImageMemoryBarrier.Buffer toColor = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image);
                toColor.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, null, null, toColor);
                check(vkEndCommandBuffer(command), "vkEndCommandBuffer(readback)");
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(command));
                check(vkQueueSubmit(queue, submit, NULL), "vkQueueSubmit(readback)");
                check(vkQueueWaitIdle(queue), "vkQueueWaitIdle(readback)");
            } finally {
                vkDestroyCommandPool(device, pool, null);
            }
        }
    }

    private void initialize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer required = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (required == null) throw new IllegalStateException("GLFW Vulkan extensions unavailable");
            LinkedHashSet<String> extensions = new LinkedHashSet<>();
            for (int i = 0; i < required.remaining(); i++) extensions.add(required.getStringUTF8(i));
            instanceExtensions = Set.copyOf(extensions);
            VkApplicationInfo app = VkApplicationInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("Lumin Standalone Smoke")).apiVersion(VK_API_VERSION_1_3);
            PointerBuffer names = stack.mallocPointer(instanceExtensions.size());
            instanceExtensions.forEach(name -> names.put(stack.UTF8(name)));
            names.flip();
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO).pApplicationInfo(app)
                    .ppEnabledExtensionNames(names);
            PointerBuffer instancePointer = stack.mallocPointer(1);
            check(vkCreateInstance(createInfo, null, instancePointer), "vkCreateInstance");
            instance = new VkInstance(instancePointer.get(0), createInfo);
            var surfacePointer = stack.longs(NULL);
            check(GLFWVulkan.glfwCreateWindowSurface(instance, window, null, surfacePointer), "glfwCreateWindowSurface");
            surface = surfacePointer.get(0);
            chooseDevice(stack);
            createDevice(stack);
        }
    }

    private void chooseDevice(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        check(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
        PointerBuffer devices = stack.mallocPointer(count.get(0));
        check(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices");
        for (int index = 0; index < count.get(0); index++) {
            VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(index), instance);
            IntBuffer familyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, familyCount, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(familyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, familyCount, families);
            for (int family = 0; family < familyCount.get(0); family++) {
                IntBuffer present = stack.ints(0);
                check(vkGetPhysicalDeviceSurfaceSupportKHR(candidate, family, surface, present), "surface support");
                int flags = VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT;
                if ((families.get(family).queueFlags() & flags) == flags && present.get(0) != 0) {
                    physicalDevice = candidate;
                    queueFamily = family;
                    VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                    vkGetPhysicalDeviceProperties(candidate, properties);
                    deviceName = properties.deviceNameString();
                    apiVersion = properties.apiVersion();
                    return;
                }
            }
        }
        throw new IllegalStateException("no graphics/compute/transfer/present Vulkan queue family");
    }

    private void createDevice(MemoryStack stack) {
        VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
        queueInfo.get(0).sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamily).pQueuePriorities(stack.floats(1f));
        VkPhysicalDeviceDynamicRenderingFeatures dynamic = VkPhysicalDeviceDynamicRenderingFeatures.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES).dynamicRendering(true);
        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pNext(dynamic.address()).pQueueCreateInfos(queueInfo)
                .ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)));
        PointerBuffer devicePointer = stack.mallocPointer(1);
        check(vkCreateDevice(physicalDevice, createInfo, null, devicePointer), "vkCreateDevice");
        device = new VkDevice(devicePointer.get(0), physicalDevice, createInfo);
        PointerBuffer queuePointer = stack.mallocPointer(1);
        vkGetDeviceQueue(device, queueFamily, 0, queuePointer);
        queue = new VkQueue(queuePointer.get(0), device);
    }

    @Override
    public void close() {
        if (closed) return;
        invalidation.invalidate();
        if (device != null) {
            vkDeviceWaitIdle(device);
            vkDestroyDevice(device, null);
            device = null;
        }
        if (instance != null && surface != NULL) {
            vkDestroySurfaceKHR(instance, surface, null);
            surface = NULL;
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }
        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
        closed = true;
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
    }
}
