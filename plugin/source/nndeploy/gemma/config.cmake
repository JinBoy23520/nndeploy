# Gemma3 Plugin CMake Configuration

set(PLUGIN_BINARY nndeploy_plugin_gemma)

file(GLOB_RECURSE PLUGIN_SOURCE
  "${ROOT_PATH}/plugin/source/nndeploy/gemma/*.cc"
)

file(GLOB_RECURSE PLUGIN_HEADER
  "${ROOT_PATH}/plugin/include/nndeploy/gemma/*.h"
)

add_library(${PLUGIN_BINARY} SHARED ${PLUGIN_SOURCE} ${PLUGIN_HEADER})

target_link_libraries(${PLUGIN_BINARY} nndeploy_framework)

if (ENABLE_NNDEPLOY_PLUGIN_PREPROCESS)
  target_link_libraries(${PLUGIN_BINARY} nndeploy_plugin_preprocess)
endif()

if (ENABLE_NNDEPLOY_PLUGIN_INFER)
  target_link_libraries(${PLUGIN_BINARY} nndeploy_plugin_infer)
endif()

if (ENABLE_NNDEPLOY_PLUGIN_TOKENIZER)
  target_link_libraries(${PLUGIN_BINARY} nndeploy_plugin_tokenizer)
endif()

set_target_properties(${PLUGIN_BINARY} PROPERTIES
  LIBRARY_OUTPUT_DIRECTORY "${BUILD_PATH}"
  ARCHIVE_OUTPUT_DIRECTORY "${BUILD_PATH}"
  RUNTIME_OUTPUT_DIRECTORY "${BUILD_PATH}"
)

install(TARGETS ${PLUGIN_BINARY}
  LIBRARY DESTINATION ${INSTALL_PATH}/lib
  ARCHIVE DESTINATION ${INSTALL_PATH}/lib
  RUNTIME DESTINATION ${INSTALL_PATH}/bin
)

install(DIRECTORY ${ROOT_PATH}/plugin/include/nndeploy/gemma
  DESTINATION ${INSTALL_PATH}/include/nndeploy
  FILES_MATCHING PATTERN "*.h"
)
